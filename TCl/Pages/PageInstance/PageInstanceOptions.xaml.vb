Imports System.IO
Imports System.Text

''' <summary>
''' 实例游戏选项页：直接读写 options.txt（复刻 LumilioCL 的「设置 › 选项」）。
''' 只编辑常用项，未知键按原样保留。
''' </summary>
Public Class PageInstanceOptions

#Region "数据"

    Private Class OptDef
        Public Key As String
        Public Title As String
        Public Group As String
        Public Kind As Integer '0 开关 / 1 滑块 / 2 下拉
        Public Min As Integer
        Public Max As Integer
        Public Percent As Boolean
        Public Choices As String()
    End Class

    Private ReadOnly Values As New Dictionary(Of String, String)(StringComparer.Ordinal)
    Private ReadOnly Order As New List(Of String)
    Private ReadOnly Defs As New List(Of OptDef)
    Private Dirty As Boolean = False
    Private Suppress As Boolean = False

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

    Private ReadOnly Property OptionsPath As String
        Get
            If Instance Is Nothing Then Return Nothing
            Return Instance.PathIndie & "options.txt"
        End Get
    End Property

#End Region

#Region "载入"

    Private Sub PageInstanceOptions_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        BuildDefs()
        LoadOptions()
    End Sub

    Public Sub Reload()
        BuildDefs()
        LoadOptions()
    End Sub

    ''' <summary>
    ''' 常用选项定义。分组顺序即界面顺序。
    ''' </summary>
    Private Sub BuildDefs()
        Defs.Clear()
        '画面
        Defs.Add(New OptDef With {.Key = "renderDistance", .Title = ModLang.L("渲染距离"), .Group = "画面", .Kind = 1, .Min = 2, .Max = 32})
        Defs.Add(New OptDef With {.Key = "simulationDistance", .Title = ModLang.L("模拟距离"), .Group = "画面", .Kind = 1, .Min = 5, .Max = 32})
        Defs.Add(New OptDef With {.Key = "graphics", .Title = ModLang.L("画质"), .Group = "画面", .Kind = 2,
            .Choices = {"fast", "fancy", "fabulous"}})
        Defs.Add(New OptDef With {.Key = "guiScale", .Title = ModLang.L("界面缩放（0 = 自动）"), .Group = "画面", .Kind = 1, .Min = 0, .Max = 4})
        Defs.Add(New OptDef With {.Key = "maxFps", .Title = ModLang.L("最大帧率"), .Group = "画面", .Kind = 1, .Min = 10, .Max = 260})
        Defs.Add(New OptDef With {.Key = "fov", .Title = ModLang.L("视野"), .Group = "画面", .Kind = 1, .Min = 30, .Max = 110})
        Defs.Add(New OptDef With {.Key = "gamma", .Title = ModLang.L("亮度"), .Group = "画面", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "particles", .Title = ModLang.L("粒子效果"), .Group = "画面", .Kind = 2,
            .Choices = {"all", "decreased", "minimal"}})
        '声音
        Defs.Add(New OptDef With {.Key = "soundCategory_master", .Title = ModLang.L("主音量"), .Group = "声音", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "soundCategory_music", .Title = ModLang.L("音乐"), .Group = "声音", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "soundCategory_blocks", .Title = ModLang.L("方块"), .Group = "声音", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "soundCategory_hostile", .Title = ModLang.L("敌对生物"), .Group = "声音", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "soundCategory_players", .Title = ModLang.L("玩家"), .Group = "声音", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        '操作
        Defs.Add(New OptDef With {.Key = "mouseSensitivity", .Title = ModLang.L("鼠标灵敏度"), .Group = "操作", .Kind = 1, .Min = 0, .Max = 200, .Percent = True})
        Defs.Add(New OptDef With {.Key = "invertYMouse", .Title = ModLang.L("反转鼠标 Y 轴"), .Group = "操作", .Kind = 0})
        Defs.Add(New OptDef With {.Key = "autoJump", .Title = ModLang.L("自动跳跃"), .Group = "操作", .Kind = 0})
        Defs.Add(New OptDef With {.Key = "toggleCrouch", .Title = ModLang.L("切换潜行"), .Group = "操作", .Kind = 0})
        Defs.Add(New OptDef With {.Key = "toggleSprint", .Title = ModLang.L("切换疾跑"), .Group = "操作", .Kind = 0})
        '聊天与语言
        Defs.Add(New OptDef With {.Key = "chatVisibility", .Title = ModLang.L("聊天可见性"), .Group = "聊天与语言", .Kind = 2,
            .Choices = {"full", "system", "commands", "hidden"}})
        Defs.Add(New OptDef With {.Key = "chatOpacity", .Title = ModLang.L("聊天不透明度"), .Group = "聊天与语言", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "chatScale", .Title = ModLang.L("聊天缩放"), .Group = "聊天与语言", .Kind = 1, .Min = 0, .Max = 100, .Percent = True})
        Defs.Add(New OptDef With {.Key = "lang", .Title = ModLang.L("游戏语言"), .Group = "聊天与语言", .Kind = 2,
            .Choices = {"zh_cn", "zh_tw", "en_us", "ja_jp", "ko_kr", "ru_ru", "de_de", "fr_fr", "es_es"}})
    End Sub

    ''' <summary>
    ''' 读取 options.txt 并按定义生成分组界面。
    ''' </summary>
    Private Sub LoadOptions()
        Values.Clear()
        Order.Clear()
        Dirty = False
        PanGroups.Children.Clear()
        LabPath.Text = If(OptionsPath, "")
        If Instance Is Nothing Then
            AddNotice(ModLang.L("请先选择一个 Minecraft 版本。"))
            Return
        End If
        If Not FileUtils.Exists(OptionsPath) Then
            AddNotice(ModLang.L("这个实例还没有 options.txt。先启动一次游戏，让游戏生成默认选项后再回来修改。"))
            Return
        End If
        Try
            For Each Line In File.ReadAllLines(OptionsPath)
                Dim Idx As Integer = Line.IndexOf(":"c)
                If Idx <= 0 Then Continue For
                Dim K As String = Line.Substring(0, Idx)
                Dim V As String = Line.Substring(Idx + 1)
                If Not Values.ContainsKey(K) Then Order.Add(K)
                Values(K) = V
            Next
        Catch ex As Exception
            Logger.Error(ex, "读取 options.txt 失败")
            AddNotice(ModLang.L("读取 options.txt 失败：") & ex.Message)
            Return
        End Try
        BuildGroups()
    End Sub

    Private Sub AddNotice(Text As String)
        Dim Pan As New MyCard With {.Margin = New Thickness(0, 0, 0, 12), .UseAnimation = False}
        Dim Stack As New StackPanel With {.Margin = New Thickness(24, 20, 20, 18)}
        Dim Block As New TextBlock With {.Text = Text, .FontSize = 13, .TextWrapping = TextWrapping.Wrap, .Opacity = 0.7}
        Block.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Stack.Children.Add(Block)
        Pan.Children.Add(Stack)
        PanGroups.Children.Add(Pan)
    End Sub

    Private Sub BuildGroups()
        PanGroups.Children.Clear()
        For Each GroupName In {"画面", "声音", "操作", "聊天与语言"}
            Dim GroupDefs As List(Of OptDef) = Defs.Where(Function(D) D.Group = GroupName AndAlso Values.ContainsKey(D.Key)).ToList()
            If GroupDefs.Count = 0 Then Continue For
            Dim Card As New MyCard With {.Margin = New Thickness(0, 0, 0, 12), .UseAnimation = False}
            Dim Pan As New StackPanel With {.Margin = New Thickness(24, 18, 22, 16)}
            Dim Head As New TextBlock With {.Text = ModLang.L(GroupName), .FontSize = 16, .FontWeight = FontWeights.SemiBold}
            Head.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
            Pan.Children.Add(Head)
            For Each D In GroupDefs
                Pan.Children.Add(BuildRow(D))
            Next
            Card.Children.Add(Pan)
            PanGroups.Children.Add(Card)
        Next
        If PanGroups.Children.Count = 0 Then
            AddNotice(ModLang.L("options.txt 存在，但没有包含可编辑的常用选项。可以在游戏内修改，或直接用文本编辑器打开它。"))
        End If
    End Sub

    ''' <summary>
    ''' 生成单个选项行：标题 + 控件 + 当前值。
    ''' </summary>
    Private Function BuildRow(D As OptDef) As UIElement
        Dim Row As New Grid With {.Margin = New Thickness(0, 12, 0, 0)}
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(190)})
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)})
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(90)})
        Dim Lab As New TextBlock With {.Text = D.Title, .FontSize = 13, .VerticalAlignment = VerticalAlignment.Center}
        Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Grid.SetColumn(Lab, 0)
        Row.Children.Add(Lab)
        Select Case D.Kind
            Case 0
                Dim Cb As New MyCheckBox With {.VerticalAlignment = VerticalAlignment.Center, .Height = 28}
                Cb.SetChecked(String.Equals(Values(D.Key), "true", StringComparison.OrdinalIgnoreCase), False)
                AddHandler Cb.Change, Sub(Sender, u)
                                          Values(D.Key) = If(CType(Sender, MyCheckBox).Checked, "true", "false")
                                          Dirty = True
                                      End Sub
                Grid.SetColumn(Cb, 1)
                Grid.SetColumnSpan(Cb, 2)
                Row.Children.Add(Cb)
            Case 1
                Dim S As New MySlider With {.MaxValue = Math.Max(D.Max - D.Min, 1), .VerticalAlignment = VerticalAlignment.Center, .Margin = New Thickness(0, 0, 16, 0)}
                S.Value = ToSlider(D)
                Dim ValText As New TextBlock With {.Text = DisplayValue(D), .FontSize = 13, .VerticalAlignment = VerticalAlignment.Center, .HorizontalAlignment = HorizontalAlignment.Right}
                ValText.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush2")
                AddHandler S.Change, Sub(Sender, u)
                                         If Suppress Then Return
                                         Values(D.Key) = FromSlider(D, CType(Sender, MySlider).Value)
                                         ValText.Text = DisplayValue(D)
                                         Dirty = True
                                     End Sub
                Grid.SetColumn(S, 1)
                Grid.SetColumn(ValText, 2)
                Row.Children.Add(S)
                Row.Children.Add(ValText)
            Case 2
                Dim C As New MyComboBox With {.Height = 32, .Width = 200, .HorizontalAlignment = HorizontalAlignment.Left}
                For Each Choice In D.Choices
                    Dim Item As New MyComboBoxItem With {.Content = Choice}
                    C.Items.Add(Item)
                Next
                Dim Current As String = Values(D.Key)
                Dim Index As Integer = Array.IndexOf(D.Choices, Current)
                If Index >= 0 Then C.SelectedIndex = Index
                AddHandler C.SelectionChanged, Sub(Sender, e)
                                                   Dim Box = CType(Sender, MyComboBox)
                                                   If Box.SelectedIndex < 0 Then Return
                                                   Values(D.Key) = D.Choices(Box.SelectedIndex)
                                                   Dirty = True
                                               End Sub
                Grid.SetColumn(C, 1)
                Grid.SetColumnSpan(C, 2)
                Row.Children.Add(C)
        End Select
        Return Row
    End Function

    ''' <summary>options.txt 里的原始值 → 滑块整数。</summary>
    Private Function ToSlider(D As OptDef) As Integer
        Dim Raw As String = Values(D.Key)
        Dim Num As Double
        If Not Double.TryParse(Raw, Globalization.NumberStyles.Float, Globalization.CultureInfo.InvariantCulture, Num) Then Return 0
        Dim Scaled As Double = If(D.Percent, Num * 100, Num)
        Return Math.Max(0, Math.Min(D.Max - D.Min, CInt(Math.Round(Scaled)) - D.Min))
    End Function

    ''' <summary>滑块整数 → options.txt 里的值文本。</summary>
    Private Function FromSlider(D As OptDef, SliderValue As Integer) As String
        Dim Actual As Integer = SliderValue + D.Min
        If D.Percent Then Return (Actual / 100.0).ToString("0.0###", Globalization.CultureInfo.InvariantCulture)
        Return Actual.ToString(Globalization.CultureInfo.InvariantCulture)
    End Function

    Private Function DisplayValue(D As OptDef) As String
        If D.Percent Then
            Return ToSlider(D) + D.Min & "%"
        End If
        Return (ToSlider(D) + D.Min).ToString()
    End Function

#End Region

#Region "保存"

    Private Sub BtnSave_Click(sender As Object, e As MouseButtonEventArgs)
        If Instance Is Nothing OrElse Not FileUtils.Exists(OptionsPath) Then
            Hint(ModLang.L("没有可保存的 options.txt。"), HintType.Red)
            Return
        End If
        If Not Dirty Then
            Hint(ModLang.L("没有修改需要保存。"), HintType.Blue)
            Return
        End If
        Try
            Dim Lines As New List(Of String)
            Dim Written As New HashSet(Of String)(StringComparer.Ordinal)
            For Each K In Order
                If Values.ContainsKey(K) Then
                    Lines.Add(K & ":" & Values(K))
                    Written.Add(K)
                End If
            Next
            For Each K In Values.Keys
                If Not Written.Contains(K) Then Lines.Add(K & ":" & Values(K))
            Next
            File.WriteAllLines(OptionsPath, Lines, New UTF8Encoding(False))
            Dirty = False
            Hint(ModLang.L("游戏选项已保存，下次启动生效。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "保存 options.txt 失败")
            Hint(ModLang.L("保存失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    Private Sub BtnReload_Click(sender As Object, e As MouseButtonEventArgs)
        If Dirty Then
            If MyMsgBox(ModLang.L("有未保存的修改，重新载入会丢弃它们。是否继续？"), ModLang.L("重新载入"),
                        ModLang.L("继续"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        End If
        LoadOptions()
        Hint(ModLang.L("已重新载入。"), HintType.Green)
    End Sub

    Private Sub BtnFolder_Click(sender As Object, e As MouseButtonEventArgs)
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        OpenExplorer(Instance.PathIndie)
    End Sub

    ''' <summary>
    ''' 把当前这套选项存为预设（只存本页认识的键，避免把无关项带进预设）。
    ''' </summary>
    Private Sub BtnSavePreset_Click(sender As Object, e As MouseButtonEventArgs)
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        Try
            Dim Name As String = MyMsgBoxInput(ModLang.L("存为选项预设"),
                ModLang.L("保存当前这张表里的全部选项，之后可以一键套用到其他实例。"))
            If String.IsNullOrWhiteSpace(Name) Then Return
            Name = Name.Trim()
            Dim ErrorText As String = ModOptionPresets.ValidateName(Name)
            If ErrorText <> "" Then
                Hint(ErrorText, HintType.Red)
                Return
            End If
            Dim Snapshot As New Dictionary(Of String, String)(StringComparer.Ordinal)
            For Each D In Defs
                Dim V As String = Nothing
                If Values.TryGetValue(D.Key, V) Then Snapshot(D.Key) = V
            Next
            Dim Result As String = ModOptionPresets.Save(Name, Snapshot)
            If Result <> "" Then
                Hint(Result, HintType.Red)
            Else
                Hint(ModLang.L("预设已保存：") & Name & ModLang.L("（") & Snapshot.Count & ModLang.L(" 项）"), HintType.Green)
            End If
        Catch ex As Exception
            Logger.Error(ex, "保存选项预设失败", LogBehavior.Alert)
        End Try
    End Sub

    ''' <summary>
    ''' 从预设套用选项：只覆盖预设里存在的键，其余键保持不动。
    ''' </summary>
    Private Sub BtnLoadPreset_Click(sender As Object, e As MouseButtonEventArgs)
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        Try
            Dim All = ModOptionPresets.ListAll()
            If Not All.Any() Then
                Hint(ModLang.L("还没有任何预设。先在选项页调好一套，再点「存为预设」。"), HintType.Red)
                Return
            End If
            '改用动画菜单卡片；原「删除预设」子菜单拍平为独立行（前缀文案区分）
            Dim Rows As New List(Of MenuCard.MenuCardRow)
            For Each P In All
                Dim One As ModOptionPresets.OptionPreset = P
                Rows.Add(New MenuCard.MenuCardRow(One.Name & ModLang.L("（") & One.Options.Count & ModLang.L(" 项）"), "lucide/sliders-horizontal", Sub() ApplyOptionPreset(One)))
            Next
            For Each P In All
                Dim One As ModOptionPresets.OptionPreset = P
                Rows.Add(New MenuCard.MenuCardRow("删除预设 · " & One.Name, "lucide/trash-2",
                Sub()
                    ModOptionPresets.Delete(One.Name)
                    Hint(ModLang.L("预设已删除：") & One.Name, HintType.Green)
                End Sub))
            Next
            MenuCard.Show(BtnLoadPreset, Rows)
        Catch ex As Exception
            Logger.Error(ex, "载入选项预设失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Sub ApplyOptionPreset(P As ModOptionPresets.OptionPreset)
        Try
            Dim Applied As Integer = 0
            Suppress = True
            Try
                For Each Pair In P.Options
                    If Defs.Any(Function(D) D.Key = Pair.Key) Then
                        Values(Pair.Key) = Pair.Value
                        Applied += 1
                    End If
                Next
            Finally
                Suppress = False
            End Try
            Dirty = True
            BuildGroups()
            Hint(ModLang.L("已套用预设「") & P.Name & ModLang.L("」，共 ") & Applied & ModLang.L(" 项。记得点「保存修改」写入 options.txt。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "套用选项预设失败", LogBehavior.Alert)
        End Try
    End Sub

#End Region

End Class
