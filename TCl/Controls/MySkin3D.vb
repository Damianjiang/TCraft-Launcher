Imports System.Windows.Media.Media3D
Imports System.Windows.Threading

''' <summary>
''' Minecraft 皮肤的 3D 预览控件。
''' <para/>
''' 坐标约定：X 向右、Y 向上、Z 指向观察者（角色正面朝 +Z），单位为皮肤像素。
''' 角色右臂/右腿在 -X（因为角色面朝 +Z，其右手落在观察者左侧）。
''' <para/>
''' 每个部件是一个长方体，贴图按"盒子展开图"标准布局（skin spec）分六个面取图：
''' <code>
'''       ┌────┬────┐
'''       │ 顶 │ 底 │          ← 顶部一条：两张 w×d
'''  ┌────┼────┼────┼────┐
'''  │ 右 │ 前 │ 左 │ 后 │     ← 中间一条：d×h、w×h、d×h、w×h
'''  └────┴────┴────┴────┘
''' </code>
''' 六个面各自用「像素矩形 → 0..1」算 UV，朝向逐一对照过（正面不镜像、背面左右反转、
''' 顶面 v 朝 +Z），不做任何整体翻转。
''' </summary>
Public Class MySkin3D
    Inherits Grid

    '—— 场景 ——
    Private ReadOnly Viewport As New Viewport3D
    Private ReadOnly ModelRoot As New Model3DGroup
    Private ReadOnly Cam As New PerspectiveCamera
    Private ReadOnly AutoTimer As New DispatcherTimer
    '旋转复用同一组变换（每帧只改角度，不重建对象）
    Private ReadOnly RotX As New RotateTransform3D(New AxisAngleRotation3D(New Vector3D(1, 0, 0), 0))
    Private ReadOnly RotY As New RotateTransform3D(New AxisAngleRotation3D(New Vector3D(0, 1, 0), 0))

    '—— 皮肤 ——
    Private _skin As BitmapSource
    Private _isSlim As Boolean
    Private _legacy As Boolean '64×32 老皮肤：左臂左腿复用右侧贴图、无独立外层

    '—— 视角 ——
    Private _yaw As Double = 22
    Private _pitch As Double = 6
    Private _zoom As Double = 1
    Private _dragging As Boolean
    Private _dragOrigin As Point
    Private _yawOrigin As Double
    Private _pitchOrigin As Double

    ''' <summary>相机到模型中心的距离（越大模型越小）。</summary>
    Private Const CamDistance As Double = 40

    ''' <summary>贴图 V 方向修正：WPF 的纹理坐标 (0,0) 在图像左下，故 V 需要翻转。</summary>
    Private Const TexFlipV As Boolean = False

    ''' <summary>空闲时是否自动旋转（默认开）。</summary>
    Public Property AutoRotate As Boolean = True

    ''' <summary>当前皮肤是否为 Alex（细手臂）模型。</summary>
    Public ReadOnly Property IsSlim As Boolean
        Get
            Return _isSlim
        End Get
    End Property

    ''' <summary>是否有可渲染的皮肤。</summary>
    Public ReadOnly Property HasSkin As Boolean
        Get
            Return _skin IsNot Nothing
        End Get
    End Property

    Public Sub New()
        ClipToBounds = True
        SnapsToDevicePixels = True
        '相机：水平 FOV 45°（WPF 的 FieldOfView 就是横向），距离按模型高 32 给足
        Cam.FieldOfView = 45
        Cam.Position = New Point3D(0, -2, CamDistance)
        Cam.LookDirection = New Vector3D(0, 0, -1)
        Cam.UpDirection = New Vector3D(0, 1, 0)
        Cam.NearPlaneDistance = 1
        Cam.FarPlaneDistance = 400
        Viewport.Camera = Cam
        '纯环境光：不产生明暗渐变，皮肤像素原色呈现
        Dim Lights As New Model3DGroup
        Lights.Children.Add(New AmbientLight(Colors.White))
        Viewport.Children.Add(New ModelVisual3D With {.Content = Lights})
        Viewport.Children.Add(New ModelVisual3D With {.Content = ModelRoot})
        Children.Add(Viewport)
        '变换组：先 X 俯仰，再 Y 偏航
        Dim View3D As New Transform3DGroup
        View3D.Children.Add(RotX)
        View3D.Children.Add(RotY)
        ModelRoot.Transform = View3D

        '自转：25fps，只在可见且未拖拽时推进，避免后台空转
        AutoTimer.Interval = TimeSpan.FromMilliseconds(40)
        AddHandler AutoTimer.Tick, AddressOf OnAutoTick
        AddHandler Loaded, Sub() AutoTimer.Start()
        AddHandler Unloaded, Sub() AutoTimer.Stop()
        AddHandler IsVisibleChanged, Sub(s, e)
            If IsVisible Then AutoTimer.Start() Else AutoTimer.Stop()
        End Sub
        ApplyView()
    End Sub

    Private Sub OnAutoTick(sender As Object, e As EventArgs)
        If Not AutoRotate OrElse _dragging OrElse _skin Is Nothing Then Return
        _yaw += 0.5
        If _yaw >= 360 Then _yaw -= 360
        ApplyView()
    End Sub

    '================================================================
    ' 对外接口
    '================================================================

    ''' <summary>
    ''' 设置皮肤贴图。传入 Nothing 时清空模型。
    ''' <paramref name="SlimOverride"/> 为空时按原版规则自动检测（右臂最外两列透明即为 Alex）。
    ''' </summary>
    Public Sub SetSkin(Source As ImageSource, Optional SlimOverride As Boolean? = Nothing)
        If Source Is Nothing Then
            ClearSkin()
            Return
        End If
        Dim Bmp As BitmapSource = TryCast(Source, BitmapSource)
        If Bmp Is Nothing Then Return
        If Bmp.PixelWidth < 64 Then Return
        _skin = Bmp
        _legacy = Bmp.PixelHeight < Bmp.PixelWidth '64×32
        _isSlim = If(SlimOverride.HasValue, SlimOverride.Value, DetectSlim(Bmp))
        BuildModel()
    End Sub

    ''' <summary>清空（显示空场景）。</summary>
    Public Sub ClearSkin()
        _skin = Nothing
        ModelRoot.Children.Clear()
    End Sub

    ''' <summary>重置视角（双击时调用）。</summary>
    Public Sub ResetView()
        _yaw = 22 : _pitch = 6 : _zoom = 1
        ApplyView()
    End Sub

    '================================================================
    ' 模型构建
    '================================================================

    ''' <summary>用当前皮肤重建全部部件（尺寸随 slim / legacy 变化，整体重建最省心）。</summary>
    Private Sub BuildModel()
        ModelRoot.Children.Clear()
        If _skin Is Nothing Then Return
        Dim SW As Double = _skin.PixelWidth
        Dim SH As Double = _skin.PixelHeight
        Dim Tex As ImageBrush = CreateTexture(_skin)
        Dim ArmW As Double = If(_isSlim, 3, 4)
        Dim ArmX As Double = (8 + ArmW) / 2 '手臂中心偏移：4 + ArmW/2

        '—— 基础层 ——
        '头 8×8×8：身体顶面在 y=6，头中心 = 6 + 4 = 10
        AddBox(Tex, 8, 8, 8, 0, 0, 0, 10, 0, SW, SH)
        '身体 8×12×4：竖直居中于 y=0（跨 -6..6）
        AddBox(Tex, 8, 12, 4, 16, 16, 0, 0, 0, SW, SH)
        '右臂（-X，贴图 40,16）；legacy 皮肤左臂复用同一块
        AddBox(Tex, ArmW, 12, 4, 40, 16, -ArmX, 0, 0, SW, SH)
        AddBox(Tex, ArmW, 12, 4, If(_legacy, 40, 32), If(_legacy, 16, 48), ArmX, 0, 0, SW, SH)
        '右腿（-X，贴图 0,16）；legacy 左腿复用
        AddBox(Tex, 4, 12, 4, 0, 16, -2, -12, 0, SW, SH)
        AddBox(Tex, 4, 12, 4, If(_legacy, 0, 16), If(_legacy, 16, 48), 2, -12, 0, SW, SH)

        '—— 外层：帽子 / 夹克 / 袖子 / 裤腿（仅 64×64 新版皮肤）——
        '外层比内层每边大 0.25（总 +0.5），避免共面闪烁；贴图仍取自各自的外层区域
        If Not _legacy Then
            AddBox(Tex, 8, 8, 8, 32, 0, 0, 10, 0, SW, SH, 0.5)
            AddBox(Tex, 8, 12, 4, 16, 32, 0, 0, 0, SW, SH, 0.5)
            AddBox(Tex, ArmW, 12, 4, 40, 32, -ArmX, 0, 0, SW, SH, 0.5)
            AddBox(Tex, ArmW, 12, 4, 48, 48, ArmX, 0, 0, SW, SH, 0.5)
            AddBox(Tex, 4, 12, 4, 0, 32, -2, -12, 0, SW, SH, 0.5)
            AddBox(Tex, 4, 12, 4, 0, 48, 2, -12, 0, SW, SH, 0.5)
        End If
        ApplyView()
    End Sub

    ''' <summary>把皮肤图包装成 3D 材质用的画刷（整图 0..1 映射，UV 直接按像素比例给出）。</summary>
    Private Function CreateTexture(Bmp As BitmapSource) As ImageBrush
        '关键：3D 材质默认按"网格 UV 的包围盒"去铺画刷，而每个部件只占贴图的一小块——
        '用相对视口会把那一小块放大铺满整个面（模型看起来像被打散的色块）。
        '必须改成绝对视口，把画刷钉死在纹理坐标 0..1，UV 才按像素比例生效。
        '清晰度：WPF 3D 的纹理采样是双线性，64×64 像素画直接上会把每个像素拉成渐变（发糊）。
        '对照 HMCL SkinCanvas.updateSkin：先用最近邻把皮肤放大到 1024 宽再上材质——
        '放大后双线性采样落在同色块内部，视觉等同游戏里的 NEAREST 过滤，像素边缘锐利。
        'UV 仍按原始皮肤宽高归一化（比例不变，自动映射到放大后的纹理）。
        Dim Multiple As Integer = Math.Max(CInt(1024 / Bmp.PixelWidth), 1)
        Dim TexImage As BitmapSource = If(Multiple > 1, EnlargeNearest(Bmp, Multiple), Bmp)
        Dim Tex As New ImageBrush(TexImage) With {
            .Stretch = Stretch.Fill,
            .ViewboxUnits = BrushMappingMode.RelativeToBoundingBox,
            .Viewbox = New Rect(0, 0, 1, 1),
            .ViewportUnits = BrushMappingMode.Absolute,
            .Viewport = New Rect(0, 0, 1, 1),
            .TileMode = TileMode.None}
        Return Tex
    End Function

    ''' <summary>最近邻放大（等效 HMCL SkinHelper.enlarge 的逐像素复制），把 64×64 像素画放大到高清纹理。</summary>
    Private Shared Function EnlargeNearest(Bmp As BitmapSource, Multiple As Integer) As BitmapSource
        Dim W As Integer = Bmp.PixelWidth * Multiple
        Dim H As Integer = Bmp.PixelHeight * Multiple
        Dim Rt As New RenderTargetBitmap(W, H, 96, 96, PixelFormats.Pbgra32)
        Dim Dv As New DrawingVisual
        Using Dc As DrawingContext = Dv.RenderOpen()
            RenderOptions.SetBitmapScalingMode(Dv, BitmapScalingMode.NearestNeighbor)
            Dc.DrawImage(Bmp, New Rect(0, 0, W, H))
        End Using
        Rt.Render(Dv)
        Rt.Freeze()
        Return Rt
    End Function

    ''' <summary>
    ''' 添加一个长方体部件。<paramref name="StartX"/>/<paramref name="StartY"/> 是展开图左上角像素坐标，
    ''' <paramref name="CX"/>/<paramref name="CY"/>/<paramref name="CZ"/> 是部件中心（皮肤像素，Y 向上）。
    ''' <paramref name="Enlarge"/> 让外层整体外扩，只改几何、不改贴图分区。
    ''' </summary>
    Private Sub AddBox(Tex As ImageBrush, W As Double, H As Double, D As Double,
                       StartX As Double, StartY As Double,
                       CX As Double, CY As Double, CZ As Double,
                       SkinW As Double, SkinH As Double, Optional Enlarge As Double = 0)
        Try
            Dim HW As Double = (W + Enlarge) / 2
            Dim HH As Double = (H + Enlarge) / 2
            Dim HD As Double = (D + Enlarge) / 2
            Dim X0 = CX - HW, X1 = CX + HW
            Dim Y0 = CY - HH, Y1 = CY + HH
            Dim Z0 = CZ - HD, Z1 = CZ + HD

            '—— 展开图六个面的像素矩形 ——
            Dim TopRect As New Rect(StartX + D, StartY, W, D)
            Dim BotRect As New Rect(StartX + D + W, StartY, W, D)
            Dim RightRect As New Rect(StartX, StartY + D, D, H)
            Dim FrontRect As New Rect(StartX + D, StartY + D, W, H)
            Dim LeftRect As New Rect(StartX + D + W, StartY + D, D, H)
            Dim BackRect As New Rect(StartX + 2 * D + W, StartY + D, W, H)

            Dim P As New Point3DCollection(24)
            Dim T As New PointCollection(24)
            Dim I As New Int32Collection(36)

            '正面 +Z
            AddQuad(P, T, I, New Point3D(X0, Y1, Z1), New Point3D(X1, Y1, Z1), New Point3D(X1, Y0, Z1), New Point3D(X0, Y0, Z1), FrontRect, SkinW, SkinH)
            '背面 -Z（从背后看，u 向 -X 增大，避免镜像）
            AddQuad(P, T, I, New Point3D(X1, Y1, Z0), New Point3D(X0, Y1, Z0), New Point3D(X0, Y0, Z0), New Point3D(X1, Y0, Z0), BackRect, SkinW, SkinH)
            '右侧 +X（从 +X 看，屏幕右方是 -Z）
            AddQuad(P, T, I, New Point3D(X1, Y1, Z1), New Point3D(X1, Y1, Z0), New Point3D(X1, Y0, Z0), New Point3D(X1, Y0, Z1), RightRect, SkinW, SkinH)
            '左侧 -X（从 -X 看，屏幕右方是 +Z）
            AddQuad(P, T, I, New Point3D(X0, Y1, Z0), New Point3D(X0, Y1, Z1), New Point3D(X0, Y0, Z1), New Point3D(X0, Y0, Z0), LeftRect, SkinW, SkinH)
            '顶面 +Y（u 向 +X，v 向 +Z）
            AddQuad(P, T, I, New Point3D(X0, Y1, Z0), New Point3D(X1, Y1, Z0), New Point3D(X1, Y1, Z1), New Point3D(X0, Y1, Z1), TopRect, SkinW, SkinH)
            '底面 -Y
            AddQuad(P, T, I, New Point3D(X0, Y0, Z1), New Point3D(X1, Y0, Z1), New Point3D(X1, Y0, Z0), New Point3D(X0, Y0, Z0), BotRect, SkinW, SkinH)

            Dim Mesh As New MeshGeometry3D With {
                .Positions = P,
                .TextureCoordinates = T,
                .TriangleIndices = I}
            Dim Material As New DiffuseMaterial(Tex)
            ModelRoot.Children.Add(New GeometryModel3D(Mesh, Material) With {.BackMaterial = Material})
        Catch ex As Exception
            Logger.Warn(ex, "3D 皮肤：构建立方体失败")
        End Try
    End Sub

    ''' <summary>加入一个四边形（4 顶点 + 2 三角形），UV 由像素矩形换算成 0..1。</summary>
    Private Shared Sub AddQuad(P As Point3DCollection, T As PointCollection, I As Int32Collection,
                               P0 As Point3D, P1 As Point3D, P2 As Point3D, P3 As Point3D,
                               R As Rect, SkinW As Double, SkinH As Double)
        Dim U0 = R.X / SkinW
        Dim U1 = (R.X + R.Width) / SkinW
        Dim V0 = R.Y / SkinH
        Dim V1 = (R.Y + R.Height) / SkinH
        'WPF 纹理坐标的 v=0 在图像下方：像素 y 对应 v = 1 - y/H，所以矩形顶边取 1-V0、底边取 1-V1
        If TexFlipV Then
            V0 = 1 - R.Y / SkinH
            V1 = 1 - (R.Y + R.Height) / SkinH
        End If
        Dim Base As Integer = P.Count
        P.Add(P0) : T.Add(New Point(U0, V0))
        P.Add(P1) : T.Add(New Point(U1, V0))
        P.Add(P2) : T.Add(New Point(U1, V1))
        P.Add(P3) : T.Add(New Point(U0, V1))
        I.Add(Base) : I.Add(Base + 1) : I.Add(Base + 2)
        I.Add(Base) : I.Add(Base + 2) : I.Add(Base + 3)
    End Sub

    ''' <summary>
    ''' 判断是否 Alex 细手臂（原版规则）：classic 皮肤右臂最外侧两列是 slim 皮肤的空白区。
    ''' </summary>
    Private Shared Function DetectSlim(Bmp As BitmapSource) As Boolean
        Try
            If Bmp.PixelWidth < 64 OrElse Bmp.PixelHeight < 64 Then Return False
            Dim Conv As New FormatConvertedBitmap(Bmp, PixelFormats.Bgra32, Nothing, 0)
            Dim Px(3) As Byte
            Conv.CopyPixels(New Int32Rect(54, 20, 1, 1), Px, 4, 0)
            Return Px(3) = 0
        Catch ex As Exception
            Logger.Warn(ex, "3D 皮肤：胖瘦检测失败，按 classic 处理")
            Return False
        End Try
    End Function

    '================================================================
    ' 视角与交互
    '================================================================

    Private Sub ApplyView()
        Try
            '先俯仰、后绕世界 Y 旋转（组内顺序 = 依次应用，yaw 放最后才不会被俯仰带歪）
            CType(RotX.Rotation, AxisAngleRotation3D).Angle = _pitch
            CType(RotY.Rotation, AxisAngleRotation3D).Angle = _yaw
            Cam.Position = New Point3D(0, -2, CamDistance / Math.Max(0.35, _zoom))
        Catch ex As Exception
            Logger.Warn(ex, "3D 皮肤：视角更新失败")
        End Try
    End Sub

    Protected Overrides Sub OnMouseLeftButtonDown(e As MouseButtonEventArgs)
        MyBase.OnMouseLeftButtonDown(e)
        If e.ClickCount = 2 Then
            ResetView()
            Return
        End If
        _dragging = True
        _dragOrigin = e.GetPosition(Me)
        _yawOrigin = _yaw
        _pitchOrigin = _pitch
        CaptureMouse()
        Cursor = Cursors.SizeAll
        e.Handled = True
    End Sub

    Protected Overrides Sub OnMouseMove(e As MouseEventArgs)
        MyBase.OnMouseMove(e)
        If Not _dragging Then Return
        Dim Pos = e.GetPosition(Me)
        _yaw = _yawOrigin + (Pos.X - _dragOrigin.X) * 0.55
        '俯仰夹紧，避免翻转后看到模型底部
        _pitch = Math.Max(-42, Math.Min(42, _pitchOrigin + (Pos.Y - _dragOrigin.Y) * 0.35))
        ApplyView()
    End Sub

    Protected Overrides Sub OnMouseLeftButtonUp(e As MouseButtonEventArgs)
        MyBase.OnMouseLeftButtonUp(e)
        If Not _dragging Then Return
        _dragging = False
        ReleaseMouseCapture()
        Cursor = Cursors.Hand
    End Sub

    Protected Overrides Sub OnMouseWheel(e As MouseWheelEventArgs)
        MyBase.OnMouseWheel(e)
        _zoom = Math.Max(0.6, Math.Min(2.2, _zoom + If(e.Delta > 0, 0.08, -0.08)))
        ApplyView()
        e.Handled = True
    End Sub

End Class
