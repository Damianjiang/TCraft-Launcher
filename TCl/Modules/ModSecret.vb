'由于包含加解密等安全信息，本文件中的部分代码已被删除

Friend Module ModSecret

    '标注 TCL 的不同分支，仅用于替换标记
    Public Const VersionBranchMain As String = "OpenSource"
    '在开源版的注册表与常规版的注册表隔离，以防数据冲突
    Public Const RegFolder As String = "TCLDebug"
    '用于微软登录的 OAuth ClientId（设备码登录流程）。
    '开源版不内置任何微软凭据；内部版由构建脚本注入 InternalSecrets.vb（密钥文件不进仓库）。
    '改用反射读取而非条件编译：MSBuild 命令行的逗号会截断 DefineConstants，INTERNAL 常量曾因此失效。
    '开源版启用方法：注册 Azure 应用后设置环境变量 TCL_MS_CLIENT_ID 为你的 ClientId，重启启动器即可。
    Public OAuthClientId As String = ResolveMsClientId()

    ''' <summary>环境变量优先；否则读取内部版注入的常量，开源版中该类型不存在则返回空串。</summary>
    Private Function ResolveMsClientId() As String
        Dim Env As String = Environment.GetEnvironmentVariable("TCL_MS_CLIENT_ID")
        If Not String.IsNullOrWhiteSpace(Env) Then Return Env.Trim
        Return ReadInternalSecret("MsClientId")
    End Function

    ''' <summary>读取内部版注入的密钥常量；开源版程序集里没有该类型，直接返回空串。</summary>
    Private Function ReadInternalSecret(FieldName As String) As String
        Try
            '项目根命名空间为 TCraft：先按全名找，再退化为按简单类型名遍历
            Dim Asm As System.Reflection.Assembly = System.Reflection.Assembly.GetExecutingAssembly()
            Dim Secrets As System.Type = Asm.GetType("TCraft.InternalSecrets")
            If Secrets Is Nothing Then
                Secrets = Array.Find(Asm.GetTypes(), Function(t As System.Type) t.Name = "InternalSecrets")
            End If
            If Secrets Is Nothing Then Return ""
            Dim Field As System.Reflection.FieldInfo = Secrets.GetField(FieldName,
                System.Reflection.BindingFlags.Public Or System.Reflection.BindingFlags.Static)
            If Field Is Nothing Then Return ""
            Return If(CStr(Field.GetValue(Nothing)), "").Trim
        Catch ex As Exception
            Return ""
        End Try
    End Function
    'CurseForge API Key
    '优先使用环境变量 TCL_CURSEFORGE_API_KEY；否则读取「设置 → 系统/下载 → 社区资源 API Key」。
    'CurseForge 的 API（包括世界/模组/整合包搜索与文件列表）要求携带 x-api-key，缺失时返回 403。
    Public Function GetCurseForgeAPIKey() As String
        Dim Key As String = Environment.GetEnvironmentVariable("TCL_CURSEFORGE_API_KEY")
        If String.IsNullOrWhiteSpace(Key) Then Key = ReadInternalSecret("CurseForgeApiKey")
        If String.IsNullOrWhiteSpace(Key) Then
            Try : Key = Settings.Get(Of String)("ToolDownloadCurseForgeKey") : Catch : Key = "" : End Try
        End If
        Return If(Key, "").Trim
    End Function
    '用于匿名数据收集的腾讯云日志服务上报 URL，形如 https://{region}.cls.tencentcs.com/track?topic_id={topic_id}
    Public Const ClsBaseUrl As String = ""

#Region "网络鉴权"

    Friend Function SecretCdnSign(UrlWithMark As String) As String
        If Not UrlWithMark.EndsWithF("{CDN}") Then Return UrlWithMark
        Return UrlWithMark.Replace("{CDN}", "").Replace(" ", "%20")
    End Function
    ''' <summary>
    ''' 设置 Headers 的 UA、Referer。
    ''' </summary>
    Friend Sub SecretHeadersSign(Url As String, ByRef Req As HttpRequestMessage, Optional SimulateBrowserHeaders As Boolean = False)
        If Not Req.Headers.UserAgent.Any Then
            If Url.Contains("baidupcs.com") OrElse Url.Contains("baidu.com") Then
                Req.Headers.Add("User-Agent", "LogStatistic")
            ElseIf SimulateBrowserHeaders Then
                Req.Headers.Add("User-Agent", $"TCL/{VersionBaseName}.{CInt(BuildType)} Mozilla/5.0 AppleWebKit/537.36 Chrome/63.0.3239.132 Safari/537.36")
            Else
                Req.Headers.Add("User-Agent", $"TCL/{VersionBaseName}.{CInt(BuildType)}")
            End If
        End If
        If Not SimulateBrowserHeaders Then Req.Headers.Add("Referer", $"http://{VersionCode}.open.tcl.server/")
        If Url.Contains("api.curseforge.com") OrElse Url.Contains("forgecdn.net") Then Req.Headers.Add("x-api-key", GetCurseForgeAPIKey())
    End Sub

#End Region

#Region "主题"

    Public Color1 As New MyColor(52, 61, 74)
    Public Color2 As New MyColor(11, 91, 203)
    Public Color3 As New MyColor(19, 112, 243)
    Public Color4 As New MyColor(72, 144, 245)
    Public Color5 As New MyColor(150, 192, 249)
    Public Color6 As New MyColor(213, 230, 253)
    Public Color7 As New MyColor(222, 236, 253)
    Public Color8 As New MyColor(234, 242, 254)
    Public ColorBg0 As New MyColor(150, 192, 249)
    Public ColorBg1 As New MyColor(190, Color7)
    Public ColorGray1 As New MyColor(64, 64, 64)
    Public ColorGray2 As New MyColor(115, 115, 115)
    Public ColorGray3 As New MyColor(140, 140, 140)
    Public ColorGray4 As New MyColor(166, 166, 166)
    Public ColorGray5 As New MyColor(204, 204, 204)
    Public ColorGray6 As New MyColor(235, 235, 235)
    Public ColorGray7 As New MyColor(240, 240, 240)
    Public ColorGray8 As New MyColor(245, 245, 245)
    Public ColorSemiTransparent As New MyColor(1, Color8)

    Public ThemeNow As Integer = -1
    Public ColorHue As Integer = 210, ColorSat As Integer = 85, ColorLightAdjust As Integer = 0, ColorHueTopbarDelta As OneOf(Of Integer, Integer()) = 0
    Public ThemeDontClick As Integer = 0
    '组件半透明程度：0 = 完全透明，100 = 完全不透明
    Public ComponentOpacity As Integer = 50
    '记录未叠加半透明的基础颜色，供组件半透明程度实时调整
    Public BaseColor6 As MyColor, BaseColor7 As MyColor, BaseColorBg1 As MyColor

    ''' <summary>
    ''' 更新组件（卡片、列表项等）的半透明程度。
    ''' 0 为完全透明，100 为完全不透明。
    ''' </summary>
    Public Sub UpdateComponentOpacity(Value As Object)
        Try
            Dim NewValue As Integer = 50
            Try
                NewValue = CInt(Value)
            Catch
                NewValue = Settings.Get(Of Integer)("UiComponentOpacity")
            End Try
            NewValue = Math.Max(0, Math.Min(100, NewValue))
            If NewValue = ComponentOpacity Then Return
            ComponentOpacity = NewValue
            Logger.Info($"组件半透明程度：{ComponentOpacity}%")
            '表面颜色（列表项、输入框、按钮等）
            ApplyComponentOpacityToColors()
            '卡片底色使用全局资源，修改一次即可全局生效
            ApplyCardOpacity()
            '列表项、提示框等底色为独立画布，需要逐个刷新
            RefreshComponentSurfaces()
        Catch ex As Exception
            Logger.Error(ex, "更新组件半透明程度失败", LogBehavior.Toast)
        End Try
    End Sub
    ''' <summary>
    ''' 按当前的组件半透明程度重新计算表面颜色。
    ''' </summary>
    Public Sub ApplyComponentOpacityToColors()
        If BaseColor7 Is Nothing Then Return
        Dim Factor = ComponentOpacity / 100
        Color6 = ScaleColor(BaseColor6, Factor)
        Color7 = ScaleColor(BaseColor7, Factor)
        ColorBg1 = ScaleColor(BaseColorBg1, Factor)
        '重新赋值资源，动态引用这些资源的控件会自动刷新
        Application.Current.Resources("ColorBrush6") = New SolidColorBrush(Color6)
        Application.Current.Resources("ColorBrush7") = New SolidColorBrush(Color7)
        Application.Current.Resources("ColorBrushBg1") = New SolidColorBrush(ColorBg1)
        '按钮等控件的半透明白色底
        Application.Current.Resources("ColorBrushHalfWhite") = New SolidColorBrush(ScaleColor(New MyColor("#55ffffff"), Factor))
    End Sub
    ''' <summary>
    ''' 按比例缩放颜色的不透明度。
    ''' </summary>
    Public Function ScaleColor(Col As MyColor, Factor As Double) As MyColor
        If Col Is Nothing Then Return Col
        Return New MyColor With {.A = Col.A * Factor, .R = Col.R, .G = Col.G, .B = Col.B}
    End Function
    ''' <summary>
    ''' 按当前的组件半透明程度计算颜色。
    ''' </summary>
    Public Function ComponentColor(Col As MyColor) As MyColor
        Return ScaleColor(Col, ComponentOpacity / 100)
    End Function
    ''' <summary>
    ''' 将组件半透明程度应用到卡片底色。
    ''' </summary>
    Private Sub ApplyCardOpacity()
        Try
            Dim Factor = ComponentOpacity / 100
            Application.Current.Resources("ColorBrushCardBg") = New SolidColorBrush(Color.FromArgb(MyColor.ClampToByte(245 * Factor), 255, 255, 255))
        Catch ex As Exception
            Logger.Warn(ex, "应用卡片半透明程度失败")
        End Try
    End Sub
    ''' <summary>
    ''' 刷新当前界面中所有组件的底色，使其跟随组件半透明程度。
    ''' </summary>
    Private Sub RefreshComponentSurfaces()
        Try
            If FrmMain Is Nothing Then Return
            RefreshComponentSurfaces(FrmMain)
        Catch ex As Exception
            Logger.Warn(ex, "刷新组件底色失败")
        End Try
    End Sub
    Private Sub RefreshComponentSurfaces(Obj As DependencyObject)
        Dim Count = VisualTreeHelper.GetChildrenCount(Obj)
        For i = 0 To Count - 1
            Dim Child = VisualTreeHelper.GetChild(Obj, i)
            If TypeOf Child Is MyListItem Then
                DirectCast(Child, MyListItem).RefreshSurfaceColor()
            ElseIf TypeOf Child Is MyHint Then
                DirectCast(Child, MyHint).RefreshSurfaceColor()
            End If
            RefreshComponentSurfaces(Child)
        Next
    End Sub

    '壁纸取色覆盖（HMCL Monet 思路的轻量实现）：>=0 时 ThemeRefresh 用它替换 ColorHue/Sat，
    '用户显式选择主题即失效，下次背景加载重新提取
    Public WallpaperHue As Integer = -1
    Public WallpaperSat As Integer = -1

    ''' <summary>
    ''' 从背景图片提取主题色：饱和度×明度加权的 36 档色相投票（向量求和防 0°/360° 回绕），
    ''' 胜出 bin 的平均色相/饱和度作为主题色，触发整套配色重新生成。纯 CPU、24×24 采样，开销可忽略。
    ''' </summary>
    Public Sub ApplyWallpaperTheme(Background As Brush)
        Dim Stage As String = "准备"
        Try
            If Not Settings.Get(Of Boolean)("UiAutoThemeFromBackground") Then Return
            If TypeOf Background IsNot ImageBrush Then Return
            Dim Source As BitmapSource = CType(Background, ImageBrush).ImageSource
            If Source Is Nothing OrElse Source.Width < 1 OrElse Source.Height < 1 Then Return
            '降采样到 24×24 并统一转 Bgra32，逐像素读取
            Stage = "降采样"
            Dim Small As New FormatConvertedBitmap(
                New TransformedBitmap(Source, New ScaleTransform(24 / Source.Width, 24 / Source.Height)),
                PixelFormats.Bgra32, Nothing, 0)
            '尺寸一律夹紧：非常规 DPI / 0 尺寸的图会让 PixelWidth 失真，
            '旧代码直接拿它算数组长度和步长，会抛 OverflowException 让整段取色失效。
            Dim W As Integer = Math.Max(1, Math.Min(64, Small.PixelWidth))
            Dim H As Integer = Math.Max(1, Math.Min(64, Small.PixelHeight))
            Dim Pixels(W * H * 4 - 1) As Byte
            Stage = "读取像素"
            Small.CopyPixels(Pixels, W * 4, 0)
            Dim BinWeight(35) As Double, BinSin(35) As Double, BinCos(35) As Double, BinSat(35) As Double, BinCount(35) As Integer
            Stage = "逐像素统计"
            For i = 0 To W * H - 1
                '通道一律提升为 Integer：直接拿 Byte 做减法（如 G - B 为负）在 VB 里会按 Byte
                '运算并抛 OverflowException，正是本方法此前一直取色失败的根因
                Dim B As Integer = Pixels(i * 4), G As Integer = Pixels(i * 4 + 1)
                Dim R As Integer = Pixels(i * 4 + 2), A As Integer = Pixels(i * 4 + 3)
                If A < 128 Then Continue For
                Dim Max = Math.Max(R, Math.Max(G, B)), Min = Math.Min(R, Math.Min(G, B))
                If Max = 0 OrElse Max = Min Then Continue For '纯灰像素无色相信息
                Dim Sat = (Max - Min) / CDbl(Max), Light = Max / 255.0
                If Sat < 0.18 OrElse Light < 0.18 OrElse Light > 0.98 Then Continue For '过滤灰、过暗、过曝
                Dim Delta = Max - Min, Hue As Double
                If Max = R Then
                    Hue = 60 * (((G - B) / Delta) Mod 6)
                ElseIf Max = G Then
                    Hue = 60 * ((B - R) / Delta + 2)
                Else
                    Hue = 60 * ((R - G) / Delta + 4)
                End If
                If Hue < 0 Then Hue += 360
                If Double.IsNaN(Hue) OrElse Double.IsInfinity(Hue) Then Continue For
                Dim Bin = Math.Max(0, Math.Min(35, CInt(Math.Floor(Hue / 10))))
                Dim Weight = Sat * Math.Min(Light, 1 - Light) * 2 '中间明度的鲜艳色权重最高
                Dim Rad = Hue / 180 * Math.PI
                BinWeight(Bin) += Weight
                BinSin(Bin) += Math.Sin(Rad) * Weight
                BinCos(Bin) += Math.Cos(Rad) * Weight
                BinSat(Bin) += Sat
                BinCount(Bin) += 1
            Next
            '综合相邻 bin（各 1/4 权重）取胜者，避免色相落在 bin 边界时结果抖动
            Stage = "选出主色"
            Dim BestBin = -1, BestWeight = 0.0
            For b = 0 To 35
                Dim Total = BinWeight(b) + BinWeight((b + 35) Mod 36) * 0.25 + BinWeight((b + 1) Mod 36) * 0.25
                If Total > BestWeight Then
                    BestWeight = Total : BestBin = b
                End If
            Next
            If BestBin < 0 OrElse BinCount(BestBin) = 0 Then Return
            Dim BestRad = Math.Atan2(BinSin(BestBin), BinCos(BestBin))
            If BestRad < 0 Then BestRad += Math.PI * 2
            Dim HueDeg As Double = BestRad / Math.PI * 180
            If Double.IsNaN(HueDeg) OrElse Double.IsInfinity(HueDeg) Then Return
            WallpaperHue = CInt(Math.Round(HueDeg)) Mod 360
            WallpaperSat = Math.Max(55, Math.Min(100, CInt(Math.Round(BinSat(BestBin) / BinCount(BestBin) * 100))))
            Stage = "应用配色"
            Logger.Info($"背景取色：色相 {WallpaperHue}，饱和度 {WallpaperSat}（主题色已跟随背景图）")
            ThemeRefresh() '不传参：保留 ThemeNow，仅让取色覆盖生效
        Catch ex As Exception
            Logger.Warn(ex, $"从背景图提取主题色失败（阶段：{Stage}）")
        End Try
    End Sub

    ''' <summary>关闭「根据背景图取色」或用户显式换主题后，还原为主题本身的配色。</summary>
    Public Sub ResetWallpaperTheme()
        If WallpaperHue < 0 Then Return
        WallpaperHue = -1
        WallpaperSat = -1
        ThemeRefresh()
    End Sub

    Public Sub ThemeRefresh(Optional NewTheme As Integer = -1)
        Try
            If ThemeNow = NewTheme AndAlso NewTheme >= 0 Then Return
            If NewTheme >= 0 Then ThemeNow = NewTheme
            '用户显式选择主题时取消壁纸取色覆盖（下次背景加载会重新提取）
            If NewTheme >= 0 Then WallpaperHue = -1

            '应用各主题的配色（开源版中该方法原为空实现）
            Select Case ThemeNow
                Case 0 '云溪蓝
                    ColorHue = 210 : ColorSat = 85 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 1 '竹露青
                    ColorHue = 165 : ColorSat = 75 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 2 '松涧绿
                    ColorHue = 110 : ColorSat = 65 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 3 '杏阳黄
                    ColorHue = 45 : ColorSat = 90 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 4 '檀木棕
                    ColorHue = 25 : ColorSat = 45 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 5 '墨夜黑
                    ColorHue = 0 : ColorSat = 0 : ColorLightAdjust = -12 : ColorHueTopbarDelta = 0
                Case 6 '桃霞粉
                    ColorHue = 335 : ColorSat = 75 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 7 '星雾紫
                    ColorHue = 275 : ColorSat = 60 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 8 '暮霞金
                    ColorHue = 40 : ColorSat = 80 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 9 '熔岩橙
                    ColorHue = 25 : ColorSat = 90 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 10 '赤焰红
                    ColorHue = 355 : ColorSat = 80 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 11 '海盐蓝
                    ColorHue = 195 : ColorSat = 100 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 12 '七曜彩
                    ColorHue = (ColorHue + 7) Mod 360 : ColorSat = 85 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 13 '曜光彩
                    Dim Rand As New Random()
                    ColorHue = Rand.Next(0, 360) : ColorSat = Rand.Next(55, 100) : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
                Case 14 '自定义
                    ColorHue = Settings.Get(Of Integer)("UiLauncherHue")
                    ColorSat = Settings.Get(Of Integer)("UiLauncherSat")
                    ColorHueTopbarDelta = Settings.Get(Of Integer)("UiLauncherDelta")
                    ColorLightAdjust = Settings.Get(Of Integer)("UiLauncherLight") - 20
                Case Else
                    ColorHue = 210 : ColorSat = 85 : ColorLightAdjust = 0 : ColorHueTopbarDelta = 0
            End Select

            '壁纸取色覆盖生效（ApplyWallpaperTheme 设置；显式换主题已在上方清除）
            If WallpaperHue >= 0 Then
                ColorHue = WallpaperHue
                ColorSat = WallpaperSat
            End If

            Color1 = New MyColor().FromHSL2(ColorHue, ColorSat * 0.2, 25 + ColorLightAdjust * 0.3)
            Color2 = New MyColor().FromHSL2(ColorHue, ColorSat, 45 + ColorLightAdjust)
            Color3 = New MyColor().FromHSL2(ColorHue, ColorSat, 55 + ColorLightAdjust)
            Color4 = New MyColor().FromHSL2(ColorHue, ColorSat, 65 + ColorLightAdjust)
            Color5 = New MyColor().FromHSL2(ColorHue, ColorSat, 80 + ColorLightAdjust * 0.4)
            Color6 = New MyColor().FromHSL2(ColorHue, ColorSat, 91 + ColorLightAdjust * 0.1)
            Color7 = New MyColor().FromHSL2(ColorHue, ColorSat, 95)
            Color8 = New MyColor().FromHSL2(ColorHue, ColorSat, 97)
            ColorBg0 = Color4 * 0.4 + Color5 * 0.4 + ColorGray4 * 0.2
            ColorBg1 = New MyColor(190, Color7)

            '记录基础颜色，并按组件半透明程度调整表面颜色
            BaseColor6 = Color6
            BaseColor7 = Color7
            BaseColorBg1 = ColorBg1
            Try
                ComponentOpacity = Settings.Get(Of Integer)("UiComponentOpacity")
            Catch
                ComponentOpacity = 50
            End Try
            Logger.Info($"组件半透明程度：{ComponentOpacity}%")
            ApplyComponentOpacityToColors()

            ColorSemiTransparent = New MyColor(1, Color8)
            Application.Current.Resources("ColorBrush1") = New SolidColorBrush(Color1)
            Application.Current.Resources("ColorBrush2") = New SolidColorBrush(Color2)
            Application.Current.Resources("ColorBrush3") = New SolidColorBrush(Color3)
            Application.Current.Resources("ColorBrush4") = New SolidColorBrush(Color4)
            Application.Current.Resources("ColorBrush5") = New SolidColorBrush(Color5)
            Application.Current.Resources("ColorBrush6") = New SolidColorBrush(Color6)
            Application.Current.Resources("ColorBrush7") = New SolidColorBrush(Color7)
            Application.Current.Resources("ColorBrush8") = New SolidColorBrush(Color8)
            Application.Current.Resources("ColorBrushBg0") = New SolidColorBrush(ColorBg0)
            Application.Current.Resources("ColorBrushBg1") = New SolidColorBrush(ColorBg1)
            Application.Current.Resources("ColorObject1") = CType(Color1, Color)
            Application.Current.Resources("ColorObject2") = CType(Color2, Color)
            Application.Current.Resources("ColorObject3") = CType(Color3, Color)
            Application.Current.Resources("ColorObject4") = CType(Color4, Color)
            Application.Current.Resources("ColorObject5") = CType(Color5, Color)
            Application.Current.Resources("ColorObject6") = CType(Color6, Color)
            Application.Current.Resources("ColorObject7") = CType(Color7, Color)
            Application.Current.Resources("ColorObject8") = CType(Color8, Color)
            Application.Current.Resources("ColorObjectBg0") = CType(ColorBg0, Color)
            Application.Current.Resources("ColorObjectBg1") = CType(ColorBg1, Color)
            '卡片底色
            ApplyCardOpacity()
            '列表项、提示框底色
            RefreshComponentSurfaces()
            ThemeRefreshMain()
        Catch ex As Exception
            Logger.Error(ex, "刷新主题颜色失败", LogBehavior.Toast)
        End Try
    End Sub
    Public Sub ThemeRefreshMain()
        RunInUi(
        Sub()
            '第 103 轮：从 IsLoaded 放宽为 PanTitle/PanForm 非 null。
            '原 IsLoaded 守卫导致 New() 里调用 ThemeRefreshMain 时早退（IsLoaded 恒 False），
            '首帧只能显示 XAML StaticResource 里编译时固定的默认蓝背景。
            'PanTitle 在 InitializeComponent 完成后即非 null，可安全访问。
            If FrmMain Is Nothing OrElse FrmMain.PanTitle Is Nothing Then Return
            '顶部条背景
            '第 89 轮：标题栏文字/图标/徽章一律白色，而黄、青、粉这类高明度色相下白字对比度只有 2~3:1
            '（用户反馈「看不清」）。这里按 WCAG 相对亮度把底色明度压到白字可读的区间，
            '蓝/绿等本来够深的主题不受影响（循环一次都不进）。
            Dim TopL As Double = 54 + ColorLightAdjust
            Dim EdgeL As Double = 48 + ColorLightAdjust
            Dim Probe As MyColor = New MyColor().FromHSL2(ColorHue, ColorSat, TopL)
            Do While MyColor.Luminance(Probe) > 0.2 AndAlso TopL > 26
                TopL -= 2
                EdgeL -= 2
                Probe = New MyColor().FromHSL2(ColorHue, ColorSat, TopL)
            Loop
            Dim Brush = New LinearGradientBrush With {.EndPoint = New Point(1, 0), .StartPoint = New Point(0, 0)}
            Dim Deltas = ColorHueTopbarDelta.Switch(Function(d) New Integer() {-d, 0, d}, Function(d) d)
            Brush.GradientStops.Add(New GradientStop With {.Offset = 0, .Color = New MyColor().FromHSL2(ColorHue + Deltas(0), ColorSat, EdgeL)})
            Brush.GradientStops.Add(New GradientStop With {.Offset = 0.5, .Color = New MyColor().FromHSL2(ColorHue + Deltas(1), ColorSat, TopL)})
            Brush.GradientStops.Add(New GradientStop With {.Offset = 1, .Color = New MyColor().FromHSL2(ColorHue + Deltas(2), ColorSat, EdgeL)})
            FrmMain.PanTitle.Background = Brush
            FrmMain.PanTitle.Background.Freeze()
            '主页面背景
            If Settings.Get(Of Boolean)("UiBackgroundColorful") Then
                Brush = New LinearGradientBrush With {.EndPoint = New Point(0.1, 1), .StartPoint = New Point(0.9, 0)}
                Brush.GradientStops.Add(New GradientStop With {.Offset = -0.1, .Color = New MyColor().FromHSL2(ColorHue - 15, ColorSat * 0.8, 91)})
                Brush.GradientStops.Add(New GradientStop With {.Offset = 0.4, .Color = New MyColor().FromHSL2(ColorHue, ColorSat * 0.8, 91)})
                Brush.GradientStops.Add(New GradientStop With {.Offset = 1.1, .Color = New MyColor().FromHSL2(ColorHue + 15, ColorSat * 0.8, 91)})
                FrmMain.PanForm.Background = Brush
            Else
                FrmMain.PanForm.Background = New MyColor(245, 245, 245)
            End If
            FrmMain.PanForm.Background.Freeze()
        End Sub)
    End Sub
    Friend Sub ThemeCheckAll(EffectSetup As Boolean)
    End Sub
    Friend Function ThemeCheckOne(Id As Integer) As Boolean
        Return True
    End Function
    Friend Function ThemeUnlock(Id As Integer, Optional ShowDoubleHint As Boolean = True, Optional UnlockHint As String = Nothing) As Boolean
        Return False
    End Function

#End Region

#Region "联网配置"

    ''' <summary>
    ''' 联网获取的配置信息。
    ''' 若获取失败或仍在获取中，可能为 Nothing。
    ''' </summary>
    Public ServerConfig As JObject

    Public ServerLoader As New LoaderTask(Of Integer, Integer)("TCL 配置更新", Sub() Logger.Info("该版本中不包含更新通知功能……"), Priority:=ThreadPriority.BelowNormal) With
        {.ReloadTimeout = 1000 * 60 * 60} '超时 1 小时

#End Region

#Region "赞助等级"

    Public ReadOnly Property CurrentRank As DonationRank
        Get
            Return DonationRank.None
        End Get
    End Property

    Public Sub InputTomatoCode(IsUpdating As Boolean)
    End Sub
    Friend Sub GenerateTomatoCode()
    End Sub

    ''' <summary>
    ''' 获取设备识别码。
    ''' 无后端的开源实现：取本机唯一标识 <see cref="RegistryUtils"/> 的 MachineGuid 作为指纹。
    ''' 同一台机器保持稳定、不同机器互不相同，用于区分来源，避免全体用户拿到完全一致的随机值。
    ''' 注意：该值会被拼进配置加解密密钥（见 <c>Settings</c>），因此返回内容必须逐字符稳定，不可改动。
    ''' </summary>
    Friend Function GetIdentify() As String
        ' 复用 TClKit 的注册表 API 读取 MachineGuid；读取失败（键缺失或权限不足）时由 TryRead 兜底为 null。
        Dim MachineGuid As String = CStr(RegistryUtils.TryRead("HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography", "MachineGuid"))
        ' 空值统一回退为占位指纹，保证返回值永远非空且形如 GUID。
        Return If(String.IsNullOrEmpty(MachineGuid), "0000-0000-0000-0000", MachineGuid)
    End Function

#End Region

End Module
