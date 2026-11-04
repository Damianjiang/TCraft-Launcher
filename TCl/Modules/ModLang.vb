Imports System.Text
Imports System.Runtime.CompilerServices
Imports Newtonsoft.Json
Imports Newtonsoft.Json.Linq

Public Module ModLang

    ''' <summary>
    ''' 界面多语言。索引：0=简体中文（原文，空表），1=English，2=繁體中文，3=文言文。
    ''' <para/>L(text)：把 text 归一化（换行统一为 LF、去首尾空白）后查译文；命中返回译文，否则原样返回。
    ''' <para/>说明：TCL 原为纯简体中文构建，本模块在运行时对界面控件套用译文。
    ''' 静态文案（导航、按钮、卡片标题、设置项、语言项等）通过字典覆盖；
    ''' 运行时拼接的动态文案（含数字的进度、崩溃分析等）不在静态表范围内，保持简体中文。
    ''' </summary>

    Private ReadOnly Dicts As Dictionary(Of Integer, Dictionary(Of String, String)) = BuildDictsSafe()
    '记录每个控件各文本属性的「原文」，以便在语言之间来回切换时总能基于原文重新翻译。
    '用 ConditionalWeakTable（弱引用键）：被销毁/不再被引用的控件条目会自动随 GC 消失，
    '避免长会话中滚动大量虚拟列表项时，已销毁控件的原文记录被强引用字典拖住无法回收（无界增长）。
    '仍被引用的控件不会提前失效，语义与原先的强引用字典一致。
    Private Originals As New ConditionalWeakTable(Of DependencyObject, List(Of String))

    '这些控件的文本由业务代码（状态机）实时经 ModLang.L() 刷新，不属于静态文案，
    '不能被「记录原文 + 套译文」机制覆盖：否则切语言后会被打回 XAML 占位文本（如启动按钮
    '卡在 "Loading / 正在加载"），且 RefreshButtonsUI 的「状态未变则跳过」保护使其无法自我修复。
    '若新增此类代码驱动的标签，把其控件 Name 加进来即可。
    Private DynamicLabelNames As New HashSet(Of String) From {"BtnLaunch", "LabVersion"}

    '一个控件上需要翻译的文本属性（容器控件内部标签会随容器属性自动更新，必须先记录、后套用）
    Private Class TextTarget
        Public Sub New(val As Func(Of String), apply As Action(Of String))
            Me.val = val : Me.apply = apply
        End Sub
        Public val As Func(Of String)
        Public apply As Action(Of String)
    End Class

    '统一换行与首尾空白，保证 XAML 里的 LF / CRLF 都能稳定命中字典键
    Private Function Norm(s As String) As String
        If s Is Nothing Then Return ""
        Return s.Replace(vbCrLf, vbLf).Replace(vbCr, vbLf).Trim()
    End Function

    '译文值的归一化：只统一换行，**不裁剪首尾空白**。
    '原因：大量拼接式词条的首尾空格是有意的（如 "Download failed: " 后面要接动态内容），
    '若在这里 Trim，译文会变成 "Download failed:xxx"，中英混排时缺空格（第 64 轮修）。
    Private Function NormValue(s As String) As String
        If s Is Nothing Then Return ""
        Return s.Replace(vbCrLf, vbLf).Replace(vbCr, vbLf)
    End Function

    '语言选项的「母体名」：简体中文/English/繁體中文/文言文 这 4 个词本身就是语言标识，
    '任何界面语言下都必须保持原样显示，否则用户切到某语言后就找不到其他语言选项（"找不到回家的路"）。
    'L() 命中即原样返回。注意：这些词无首尾空白/换行，Norm(x)=x，故与 L() 里 Norm 后的 Key 可比。
    Private ProtectedLanguageNames As New HashSet(Of String) From {
        "简体中文", "English", "繁體中文", "文言文"
    }

    '外置翻译文件（用户可编辑，重启后生效）。与内置字典同源，格式见 ExportBuiltinTranslations。
    Private I18nFile As String
    Private I18nLoaded As Boolean = False

    Public Function L(Original As String) As String
        If String.IsNullOrEmpty(Original) Then Return Original
        Try
            '性能快路径：绝大多数词条没有换行、也没有首尾空白，此时无需 Norm 的两次 Replace + Trim
            Dim Key As String
            If Original.IndexOf(ChrW(13)) < 0 AndAlso Original.IndexOf(ChrW(10)) < 0 AndAlso
               Not Char.IsWhiteSpace(Original(0)) AndAlso Not Char.IsWhiteSpace(Original(Original.Length - 1)) Then
                Key = Original
            Else
                Key = Norm(Original)
            End If
            '语言选项母体名永不翻译（任何界面语言下都保持原样，保证语言切换永远可达）
            If ProtectedLanguageNames.Contains(Key) Then Return Original
            Dim Lang As Integer = ModBase.ResolveLanguage()
            If Lang < 0 OrElse Lang >= Dicts.Count OrElse Dicts(Lang) Is Nothing Then Return Original
            Dim v As String = ""
            '所有语言（含 0=简体中文）都从字典/文件取值：i18n.json 是「全部界面文案」的单一可编辑来源，
            '中文若未被用户在文件里改写则命中不到，原样返回（XAML 里的中文原文即默认值）。
            If Dicts(Lang).TryGetValue(Key, v) Then Return v
        Catch ex As Exception
            Logger.Warn(ex, "翻译界面文本失败（保持原文显示）")
        End Try
        Return Original
    End Function

    ''' <summary>
    ''' L() 的崩溃路径安全版：再包一层 Try/Catch。
    ''' <para/>L() 内部虽有整体 Try/Catch，但其本体首次 JIT 时需解析 TClKit（Logger）——
    ''' 若在启动最外层错误处理（Application_Startup 的 Catch、TclLogger.HandleBehavior）中调用，
    ''' 此时触发处理的异常本身可能就是程序集加载失败，L() 的 JIT 会再抛而炸掉错误处理链。
    ''' 此方法保证任何情况下都不二次抛出：JIT 失败即原样返回，弹窗退化为简体中文而非崩溃。
    ''' </summary>
    Public Function LSafe(Original As String) As String
        Try
            Return L(Original)
        Catch
            Return Original
        End Try
    End Function

    ''' <summary>
    ''' 后台预热入口（第 111 轮启动提速）：触碰 Dicts 强制触发模块初始化（词典构建，~85-97ms 纯 CPU）。
    ''' 启动期在后台线程调用；此后任何线程首次触碰本模块都会因初始化已完成而零等待。
    ''' </summary>
    Public Sub Warmup()
        Dim LangCount As Integer = Dicts.Count
        Logger.Trace($"ModLang 词典预热完成（{LangCount} 种语言）")
    End Sub

    ''' <summary>
    ''' 外置翻译文件路径（<c>TCL\i18n.json</c>，与 Log1.txt 同目录）。
    ''' 文件存在时，其中的条目会覆盖同名内置条目；用户改文件后调 ReloadTranslations 即可热更。
    ''' </summary>
    Public Function I18nFilePath() As String
        If String.IsNullOrEmpty(I18nFile) Then I18nFile = Paths.Base & "TCL\i18n.json"
        Return I18nFile
    End Function

    ''' <summary>
    ''' 把内置词典 + 外置覆盖合并后，序列化为 i18n.json 写到文件（供用户查看/编辑/导出）。
    ''' 返回写入的文件路径；失败返回 Nothing。
    ''' </summary>
    Public Function ExportBuiltinTranslations() As String
        Try
            '先确保外置覆盖已并入内存字典（幂等），这样导出的文件始终包含用户已改条目
            ReloadTranslations()
            '0 段自愈：全新环境（尚无 i18n.json）时 Dicts(0) 为空，导出的文件会缺「简体中文基准段」，
            '违背「i18n.json 是所有语言的单一可编辑来源」的设计（第 66 轮修复）。把内置词条的键补种进 0 段
            '（键=值=原文）；已存在的条目（用户改写过的中文基准，经 ReloadTranslations 并入 Dicts(0)）原样保留。
            If Dicts(0) IsNot Nothing AndAlso Dicts(1) IsNot Nothing Then
                For Each Pair In Dicts(1)
                    If Not Dicts(0).ContainsKey(Pair.Key) Then Dicts(0)(Pair.Key) = Pair.Key
                Next
            End If
            Dim Root As New JObject
            '「0」段 = 简体中文基准文案（键与值默认为同一原文）：让 i18n.json 成为**所有语言**的完整
            '单一来源——用户想改某条中文也直接改这里，不必动 XAML。1/2/3 段分别为英/繁/文言。
            For i As Integer = 0 To Dicts.Count - 1
                Dim LangObj As New JObject
                For Each Pair In Dicts(i)
                    LangObj(Pair.Key) = Pair.Value
                Next
                Root(i.ToString) = LangObj
            Next
            DirectoryUtils.Create(I18nFilePath().BeforeLast("\") & "\")
            FileUtils.Write(I18nFilePath(), JsonConvert.SerializeObject(Root, Formatting.Indented), Encoding.UTF8)
            Return I18nFilePath()
        Catch ex As Exception
            Logger.Warn(ex, "导出翻译文件失败")
            Return Nothing
        End Try
    End Function

    ''' <summary>
    ''' 确保 i18n.json 存在：不存在则用内置词典自动生成（首次启动即有可编辑的单文件）；
    ''' 已存在则不覆盖（保留用户已改的条目）。返回文件路径，失败返回 Nothing。
    ''' </summary>
    Public Function EnsureI18nFile() As String
        Try
            If Not FileUtils.Exists(I18nFilePath()) Then ExportBuiltinTranslations()
            If FileUtils.Exists(I18nFilePath()) Then Return I18nFilePath()
            Return Nothing
        Catch ex As Exception
            Logger.Warn(ex, "生成翻译文件失败")
            Return Nothing
        End Try
    End Function

    ''' <summary>
    ''' 读取外置 i18n.json（若存在），把其中条目合并进各语言字典（覆盖同名内置条目），并即时重套一遍译文。
    ''' 用户改完文件后调用；也供「重载翻译」入口复用。返回是否成功加载了文件。
    ''' </summary>
    Public Function ReloadTranslations() As Boolean
        Dim Text = FileUtils.TryReadAsString(I18nFilePath(), Encoding.UTF8)
        If String.IsNullOrWhiteSpace(Text) Then
            I18nLoaded = False
            Return False
        End If
        Try
            Dim Root = JObject.Parse(Text)
            '0=简体中文基准段也读入：i18n.json 是「所有语言」的完整单一来源，中文基准可在此被用户改写。
            For i As Integer = 0 To Dicts.Count - 1
                If Root(i.ToString) Is Nothing Then Continue For
                For Each Prop In CType(Root(i.ToString), JObject).Properties
                    Dicts(i)(Prop.Name) = NormValue(Prop.Value.ToString)
                Next
            Next
            RevMaps = Nothing '外置覆盖可能改写译文值，反向映射需重建（第 68 轮）
            I18nLoaded = True
            Logger.Info($"已加载外置翻译文件 {I18nFilePath()}（{Root.Count} 个语言段）")
            '即时把已记录的原文重新套一遍，让改动立刻可见
            ApplyToWindow()
            Return True
        Catch ex As Exception
            Logger.Warn(ex, "加载外置翻译文件失败（保持内置译文）")
            Return False
        End Try
    End Function

    ''' <summary>切换语言时，把主窗口当前可视树里所有用户可见文字控件统一套一遍译文（基于原文）。</summary>
    Public Sub ApplyToWindow()
        Try
            '第 N 轮：不只翻主窗口 FrmMain，翻所有已加载的顶层窗口（独立弹窗/子窗口）。
            '切语言时若某弹窗已打开且不在主窗口可视树内，原先会残留旧语言；
            'TranslateNode 幂等（基于已记录原文重套译文），重复翻无害。
            For Each w As System.Windows.Window In System.Windows.Application.Current.Windows
                If w.IsLoaded Then TranslateNode(w)
            Next
        Catch ex As Exception
            Logger.Warn(ex, "应用界面语言失败")
        End Try
    End Sub

    ''' <summary>对任意子树即时套一遍译文。供页面切换时，把刚实例化/刚显示的新页面立刻翻成当前语言，避免「没切过语言的页面还停在旧语言」。</summary>
    Public Sub ApplyTo(root As DependencyObject)
        Try
            If root Is Nothing Then Return
            Dim Lang As Integer = ModBase.ResolveLanguage()
            If Lang >= Dicts.Count Then Return
            '0=简体中文默认即原文，无需翻；但用户在 i18n.json「0」段改写过中文基准时（Dicts(0) 非空）须套用，
            '让「中文文案也可在文件里改」真正生效。
            If Lang = 0 AndAlso (Dicts(0) Is Nothing OrElse Dicts(0).Count = 0) Then Return
            TranslateNode(root)
        Catch ex As Exception
            Logger.Warn(ex, "应用译文到界面树失败（保持原文显示）")
        End Try
    End Sub

    '——反向映射（第 68 轮）：值 → 键——
    '用于把「已被翻译的文本」还原为词典键。场景：列表项等由代码以 L() 按当前语言构建，
    '翻译机制随后才记录原文；若记录的是译文，语言切换后 L(译文) 永远命不中，文本被钉死。
    Private RevMaps As Dictionary(Of Integer, Dictionary(Of String, String))
    Private Function GetRevMap(Lang As Integer) As Dictionary(Of String, String)
        If RevMaps Is Nothing Then RevMaps = New Dictionary(Of Integer, Dictionary(Of String, String))
        Dim M As Dictionary(Of String, String) = Nothing
        If Not RevMaps.TryGetValue(Lang, M) Then
            M = New Dictionary(Of String, String)(StringComparer.Ordinal)
            If Lang >= 0 AndAlso Lang < Dicts.Count AndAlso Dicts(Lang) IsNot Nothing Then
                For Each Pair In Dicts(Lang)
                    Dim V As String = Norm(Pair.Value)
                    If V <> "" AndAlso Not M.ContainsKey(V) Then M(V) = Pair.Key
                Next
            End If
            RevMaps(Lang) = M
        End If
        Return M
    End Function
    Private Function ResolveOriginalKey(Text As String, Lang As Integer) As String
        Try
            Dim Key As String = ""
            If Text IsNot Nothing AndAlso Lang >= 0 AndAlso Lang < Dicts.Count AndAlso GetRevMap(Lang).TryGetValue(Text, Key) Then Return Key
        Catch
            '反向映射失败时退回原文本，不影响记录
        End Try
        Return Text
    End Function

    '——语言切换后的「活翻译」（第 66 轮）——
    '切换语言那一刻不在可视树里的内容（虚拟化列表尚未滚到的项、下拉框展开时才实例化的 ComboBoxItem、
    '右键菜单、Tooltip、之后才创建的弹窗内容等），树遍历（ApplyToWindow/ApplyTo）永远覆盖不到，
    '它们加载后会显示 XAML 原文（简中），造成「界面一半新语言一半简中」的混乱。
    '方案：注册 FrameworkElement.Loaded 类处理器，任何元素加载即对其子树套一遍译文（幂等，
    '原文记录表保证不会二次污染）。仅非简中语言下生效；注册动作经 Dispatcher 延迟到启动首波
    'Loaded 广播结束之后，避免对整棵主窗口树逐元素重复遍历（O(n·depth)）拖慢启动。
    Private LiveTranslateHooked As Boolean = False
    Private LiveTranslateEnabled As Boolean = False
    Private LiveTranslateLang As Integer = 0

    ''' <summary>
    ''' 由 ApplyToolLanguage 在语言应用/切换时调用：更新当前语言并按需启用「活翻译」钩子。
    ''' Lang=0（简中）时钩子空转——简中即原文，无需翻译，也保持零开销。
    ''' </summary>
    Public Sub SetLiveTranslate(Lang As Integer)
        LiveTranslateLang = Lang
        LiveTranslateEnabled = (Lang <> 0)
        If LiveTranslateEnabled AndAlso Not LiveTranslateHooked Then
            LiveTranslateHooked = True
            '延迟注册：此刻可能仍处于启动 Loaded 广播中（类处理器一旦注册无法注销，只能常驻+开关）
            System.Windows.Threading.Dispatcher.CurrentDispatcher.BeginInvoke(
                System.Windows.Threading.DispatcherPriority.Background,
                New Action(Sub()
                    Try
                        EventManager.RegisterClassHandler(GetType(FrameworkElement), FrameworkElement.LoadedEvent,
                            New RoutedEventHandler(AddressOf OnElementLoadedLive), True)
                    Catch ex As Exception
                        LiveTranslateHooked = False
                        Logger.Warn(ex, "注册活翻译钩子失败（语言切换后新加载的内容可能保持简中）")
                    End Try
                End Sub))
        End If
    End Sub

    Private Sub OnElementLoadedLive(sender As Object, e As RoutedEventArgs)
        If Not LiveTranslateEnabled OrElse LiveTranslateLang = 0 Then Return
        Try
            Dim el = TryCast(sender, FrameworkElement)
            If el Is Nothing Then Return
            TranslateNode(el)
        Catch
            'Loaded 是高频路径，任何异常都只能静默吞掉，绝不能影响界面加载
        End Try
    End Sub

    '对指定根节点的可视子树统一套译文（两遍：先记录所有「原文」，再套译文；幂等可重复调用）
    '两遍是关键：复合控件（按钮/卡片等）的 Text 属性会联动其内部标签，若先套翻译再记录，
    '内部标签的「原文」会被记成已翻译文本，之后来回切换语言就会卡死在第一个切到的语言。
    Private Sub TranslateNode(root As DependencyObject)
        Dim Nodes = Collect(root)
        '性能：GetTargets 会为每个元素新建一组闭包，两遍各算一次等于翻倍分配。
        '这里先算一次存进与 Nodes 等长的并行数组，两遍共用（切换语言在可视树较大时约省一半开销）。
        Dim AllTargets As New List(Of List(Of TextTarget))(Nodes.Count)
        For Each el In Nodes
            Dim T As List(Of TextTarget) = Nothing
            Try
                If Not IsDynamicLabel(el) Then T = GetTargets(el) '代码驱动的标签（如启动按钮）不参与
            Catch ex As Exception
                Logger.Warn(ex, "收集界面文本失败（该元素不参与翻译）")
            End Try
            AllTargets.Add(T)
        Next
        '第一遍：只记录尚未记录的「原文」，不改动任何控件文本
        For i As Integer = 0 To Nodes.Count - 1
            Try
                Dim el = Nodes(i)
                Dim Targets = AllTargets(i)
                If Targets Is Nothing Then Continue For
                Dim Recs As List(Of String) = Nothing
                If Not Originals.TryGetValue(el, Recs) Then
                    Recs = New List(Of String)
                    Dim LangNow As Integer = ModBase.ResolveLanguage()
                    For Each T In Targets
                        Dim txt As String = T.val()
                        '第 68 轮修复：代码可能已按当前语言把「译文」设进控件（如列表项以 L() 构建），
                        '此时记录译文为原文会让该文本在切换语言后永远停留（L(译文) 命不中）。
                        '用反向映射把译文还原为词典键再记录，任何语言下都能正确往返。
                        Recs.Add(If(String.IsNullOrEmpty(txt), Nothing, ResolveOriginalKey(Norm(txt), LangNow)))
                    Next
                    Originals.Add(el, Recs)
                End If
            Catch ex As Exception
                Logger.Warn(ex, "记录界面原文失败（该项将不被翻译）")
            End Try
        Next
        '第二遍：基于记录的原文套当前语言译文
        For i As Integer = 0 To Nodes.Count - 1
            Try
                Dim Targets = AllTargets(i)
                If Targets Is Nothing Then Continue For
                Dim Recs As List(Of String) = Nothing
                If Not Originals.TryGetValue(Nodes(i), Recs) OrElse Recs Is Nothing Then Continue For
                For j As Integer = 0 To Targets.Count - 1
                    Dim base As String = Recs(j)
                    If base Is Nothing Then Continue For
                    Targets(j).apply(L(base))
                Next
            Catch ex As Exception
                Logger.Warn(ex, "套用译文到界面失败（该项保持原文）")
            End Try
        Next
    End Sub

    ''' <summary>
    ''' 该控件是否属于"代码驱动"的标签（其文本由业务状态机实时经 L() 刷新）。
    ''' 这类标签不参与 i18n 的「记录原文 + 套译文」流程，避免切语言时被覆盖回占位文本。
    ''' 除了控件自身 Name 命中，也向上找最近的命名可视祖先（如 MyButton 内部模板标签
    ''' LabText 的 Name 不唯一，但其祖先按钮 Name 唯一），命中即排除。
    ''' </summary>
    Private Function IsDynamicLabel(el As DependencyObject) As Boolean
        If el Is Nothing Then Return False
        Dim node As DependencyObject = el
        For i As Integer = 0 To 5
            If node Is Nothing Then Exit For
            Dim fe As System.Windows.FrameworkElement = TryCast(node, System.Windows.FrameworkElement)
            If fe IsNot Nothing AndAlso Not String.IsNullOrEmpty(fe.Name) Then
                If DynamicLabelNames.Contains(fe.Name) Then Return True
            End If
            node = System.Windows.Media.VisualTreeHelper.GetParent(node)
        Next
        Return False
    End Function

    '取某个控件上需要翻译的文本槽（无则返回 Nothing）；复合控件只翻其属性，内部标签随属性回调自动更新
    Private Function GetTargets(el As DependencyObject) As List(Of TextTarget)
        Try
            Dim Lst As New List(Of TextTarget)
            If TypeOf el Is System.Windows.Controls.TextBlock Then
                Dim tb = CType(el, System.Windows.Controls.TextBlock)
                Lst.Add(New TextTarget(Function() tb.Text, Sub(v) tb.Text = v))
            ElseIf TypeOf el Is MyButton Then
                Dim b = CType(el, MyButton)
                Lst.Add(New TextTarget(Function() b.Text, Sub(v) b.Text = v))
            ElseIf TypeOf el Is MyTextButton Then
                Dim t2 = CType(el, MyTextButton)
                Lst.Add(New TextTarget(Function() t2.Text, Sub(v) t2.Text = v))
            ElseIf TypeOf el Is MyCheckBox Then
                Dim cb = CType(el, MyCheckBox)
                Lst.Add(New TextTarget(Function() cb.Text, Sub(v) cb.Text = v))
            ElseIf TypeOf el Is MyExtraTextButton Then
                Dim et = CType(el, MyExtraTextButton)
                Lst.Add(New TextTarget(Function() et.Text, Sub(v) et.Text = v))
            ElseIf TypeOf el Is MyIconTextButton Then
                Dim it = CType(el, MyIconTextButton)
                Lst.Add(New TextTarget(Function() it.Text, Sub(v) it.Text = v))
            ElseIf TypeOf el Is MyRadioBox Then
                Dim rb = CType(el, MyRadioBox)
                Lst.Add(New TextTarget(Function() rb.Text, Sub(v) rb.Text = v))
            ElseIf TypeOf el Is MyRadioButton Then
                Dim rd = CType(el, MyRadioButton)
                Lst.Add(New TextTarget(Function() rd.Text, Sub(v) rd.Text = v))
            ElseIf TypeOf el Is MyLoading Then
                Dim ld = CType(el, MyLoading)
                Lst.Add(New TextTarget(Function() ld.Text, Sub(v) ld.Text = v))
            ElseIf TypeOf el Is MyCard Then
                Dim c = CType(el, MyCard)
                Lst.Add(New TextTarget(Function() c.Title, Sub(v) c.Title = v))
            ElseIf TypeOf el Is MyListItem Then
                Dim li = CType(el, MyListItem)
                Lst.Add(New TextTarget(Function() li.Title, Sub(v) li.Title = v))
                Lst.Add(New TextTarget(Function() li.Info, Sub(v) li.Info = v))
            ElseIf TypeOf el Is MyComboBoxItem Then
                Dim cbi = CType(el, MyComboBoxItem)
                Dim setter As Action(Of String) = Sub(v)
                    If v IsNot Nothing AndAlso TypeOf cbi.Content Is String Then
                        cbi.Content = v
                    End If
                End Sub
                Lst.Add(New TextTarget(Function() If(TypeOf cbi.Content Is String, CStr(cbi.Content), Nothing), setter))
            ElseIf TypeOf el Is MyHint Then
                Dim h = CType(el, MyHint)
                Lst.Add(New TextTarget(Function() h.Text, Sub(v) h.Text = v))
            ElseIf TypeOf el Is MyComboBox Then
                Dim co = CType(el, MyComboBox)
                Lst.Add(New TextTarget(Function() co.HintText, Sub(v) co.HintText = v))
            ElseIf TypeOf el Is MySearchBox Then
                Dim sb = CType(el, MySearchBox)
                Lst.Add(New TextTarget(Function() sb.HintText, Sub(v) sb.HintText = v))
            ElseIf TypeOf el Is MyTextBox Then
                Dim tx = CType(el, MyTextBox)
                Lst.Add(New TextTarget(Function() tx.HintText, Sub(v) tx.HintText = v))
            ElseIf TypeOf el Is System.Windows.Controls.ContentControl Then
                '通用兜底：原生 WPF ContentControl（Button / Label / ContentControl / 菜单项…）
                '携带「字符串」Content 的一并翻译。已处理的 My* 专用控件走上面的分支，不会重复；
                'Content 为子控件/非字符串时 val() 返回 Nothing，套用阶段自动跳过（安全）。
                Dim cc = CType(el, System.Windows.Controls.ContentControl)
                Lst.Add(New TextTarget(
                    Function() If(TypeOf cc.Content Is String, CStr(cc.Content), Nothing),
                    Sub(v)
                        If v IsNot Nothing AndAlso TypeOf cc.Content Is String Then cc.Content = v
                    End Sub))
            End If
            '原生标题属性（MenuItem / Menu 的 Header，字符串时翻译）
            If TypeOf el Is System.Windows.Controls.HeaderedContentControl Then
                Dim hc = CType(el, System.Windows.Controls.HeaderedContentControl)
                Lst.Add(New TextTarget(
                    Function() If(TypeOf hc.Header Is String, CStr(hc.Header), Nothing),
                    Sub(v)
                        If v IsNot Nothing AndAlso TypeOf hc.Header Is String Then hc.Header = v
                    End Sub))
            End If
            '通用控件属性：ToolTip（当其为字符串时随控件一起翻译，覆盖各页图标按钮的提示文字）
            If TypeOf el Is System.Windows.FrameworkElement Then
                Dim fe = CType(el, System.Windows.FrameworkElement)
                Lst.Add(New TextTarget(
                    Function() If(TypeOf fe.ToolTip Is String, CStr(fe.ToolTip), Nothing),
                    Sub(v)
                        If v IsNot Nothing AndAlso TypeOf fe.ToolTip Is String Then fe.ToolTip = v
                    End Sub))
            End If
            If Lst.Count = 0 Then Return Nothing
            Return Lst
        Catch ex As Exception
            Logger.Warn(ex, "收集可翻译文本失败（跳过该元素）")
        End Try
        Return Nothing
    End Function

    Private Function Collect(root As DependencyObject) As List(Of DependencyObject)
        Dim R = New List(Of DependencyObject)
        Walk(root, R)
        Return R
    End Function

    Private Sub Walk(node As DependencyObject, R As List(Of DependencyObject))
        If node Is Nothing Then Return
        R.Add(node)
        Dim N = System.Windows.Media.VisualTreeHelper.GetChildrenCount(node)
        For i = 0 To N - 1
            Walk(System.Windows.Media.VisualTreeHelper.GetChild(node, i), R)
        Next
    End Sub

    '三语同时写入，键/值均归一化，避免多行文案换行不一致导致漏译
    Private Sub Add3(En As Dictionary(Of String, String), Tw As Dictionary(Of String, String), Ww As Dictionary(Of String, String),
                     key As String, vEn As String, vTw As String, vWw As String)
        Dim K As String = Norm(key)
        En(K) = NormValue(vEn)
        Tw(K) = NormValue(vTw)
        Ww(K) = NormValue(vWw)
    End Sub

    '安全包裹：词典构建若抛异常（如某些运行时 Chr 调用失败），降级为空词典而非毒化整个
    '模块。.NET 中模块级 .cctor 抛出的异常会永久毒化该类型（后续任何访问都再抛
    'TypeInitializationException），导致所有 L() 静默失败、全界面卡死中文。此处捕获后
    '返回空词典，程序保持可用，并写日志提示。
    Private Function BuildDictsSafe() As Dictionary(Of Integer, Dictionary(Of String, String))
        Try
            Return BuildDicts()
        Catch ex As Exception
            Logger.Error(ex, "i18n 词典构建失败（该段降级为空词典，程序保持可用）")
            Return New Dictionary(Of Integer, Dictionary(Of String, String))
        End Try
    End Function

    Private Function BuildDicts() As Dictionary(Of Integer, Dictionary(Of String, String))
        Dim En = New Dictionary(Of String, String)
        Dim Tw = New Dictionary(Of String, String)
        Dim Ww = New Dictionary(Of String, String)

        '================================================================
        '内置译表（第 111 轮体积优化）：原先约 3100 行 Add3(...) 字面量把 ~500KB 的 UTF-16
        '文本塞进程序集 #US 堆；现外移为 gzip 嵌入资源 LangTables.txt.gz（UTF-8 行式，约 227KB），
        '由 scripts\gen_langtables.py 生成（解析历史 Add3 调用，语义保持「同键后者覆盖前者」）。
        '行格式：原文<TAB>英<TAB>繁<TAB>文言；值内的 Tab/LF/CR/反斜杠转义为 \t \n \r \\。
        '数据仍经 Add3 的 Norm/NormValue 归一化，构建结果与原字面量逐字节一致
        '（验证方式：删除运行目录 i18n.json 后启动，自动导出的 i18n.json 与基线 diff 为空）。
        '================================================================
        Dim Raw = ModGzip.Unwrap(LoadLangTablesResource())
        Dim TableText = Encoding.UTF8.GetString(Raw)
        For Each TableRow In TableText.Split(CChar(vbLf))
            If TableRow.Length = 0 Then Continue For
            Dim F = SplitLangRow(TableRow)
            Add3(En, Tw, Ww, F(0), F(1), F(2), F(3))
        Next

        Dim D As New Dictionary(Of Integer, Dictionary(Of String, String))
        D(0) = New Dictionary(Of String, String)
        D(1) = En
        D(2) = Tw
        D(3) = Ww
        Return D
    End Function

    '读取内嵌译表资源（gz）。清单资源名随构建命名规则而变，用 EndsWith 兜底匹配。
    Private Function LoadLangTablesResource() As Byte()
        Dim Asm = System.Reflection.Assembly.GetExecutingAssembly()
        Dim Name = Asm.GetManifestResourceNames().
            FirstOrDefault(Function(N) N.EndsWith("LangTables.txt.gz", StringComparison.OrdinalIgnoreCase))
        If Name Is Nothing Then Throw New System.IO.FileNotFoundException("内嵌译表资源缺失（LangTables.txt.gz）")
        Using Stream = Asm.GetManifestResourceStream(Name)
            Using Ms As New System.IO.MemoryStream()
                Stream.CopyTo(Ms)
                Return Ms.ToArray()
            End Using
        End Using
    End Function

    '解析一行译表：反斜杠转义（\\ \t \n \r），真实 TAB 为字段分隔符。
    Private Function SplitLangRow(TableRow As String) As String()
        Dim Fields As New List(Of String)
        Dim Cur As New StringBuilder
        Dim i = 0
        While i < TableRow.Length
            Dim c = TableRow(i)
            If c = "\"c AndAlso i + 1 < TableRow.Length Then
                i += 1
                Select Case TableRow(i)
                    Case "t"c : Cur.Append(vbTab)
                    Case "n"c : Cur.Append(vbLf)
                    Case "r"c : Cur.Append(vbCr)
                    Case "\"c : Cur.Append("\"c)
                    Case Else : Cur.Append("\"c).Append(TableRow(i))
                End Select
            ElseIf c = vbTab Then
                Fields.Add(Cur.ToString())
                Cur.Clear()
            Else
                Cur.Append(c)
            End If
            i += 1
        End While
        Fields.Add(Cur.ToString())
        If Fields.Count <> 4 Then Throw New System.IO.InvalidDataException("译表行字段数错误（" & Fields.Count & "）")
        Return Fields.ToArray()
    End Function


End Module
