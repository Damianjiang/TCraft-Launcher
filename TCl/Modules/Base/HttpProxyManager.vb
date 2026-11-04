Imports System.Net
Imports Microsoft.Win32

''' <summary>
''' 全局 HTTP 代理管理器。
''' 支持 无代理 / 系统代理 / 自定义代理 三种模式，
''' 通过实现 IWebProxy 注入到 HttpClientHandler 与 WebRequest。
''' </summary>
Public Class HttpProxyManager
    Implements IWebProxy

    Public Enum ProxyMode
        NoProxy = 0
        SystemProxy = 1
        CustomProxy = 2
    End Enum

    ''' <summary>
    ''' 全局唯一实例。
    ''' </summary>
    Public Shared ReadOnly Instance As New HttpProxyManager

    Private _Mode As ProxyMode = ProxyMode.SystemProxy
    Private _CustomAddress As Uri = Nothing
    Private _CustomCredentials As ICredentials = Nothing
    Private _CustomProxy As WebProxy = Nothing
    Private _SystemProxy As IWebProxy = Nothing
    Private ReadOnly _Lock As New Object

    Public Sub New()
        _CustomProxy = New WebProxy(New Uri("http://localhost"), True) '默认地址会被 Configure 覆盖
        RefreshSystemProxy()
    End Sub

    ''' <summary>
    ''' 根据设置项刷新代理管理器状态。
    ''' </summary>
    Public Sub Configure(Mode As ProxyMode, CustomAddress As String, CustomUsername As String, CustomPassword As String)
        SyncLock _Lock
            _Mode = Mode
            _CustomProxy.Credentials = Nothing
            If Mode = ProxyMode.CustomProxy AndAlso CustomAddress.Any Then
                Try
                    Dim Address As String = CustomAddress.Trim
                If Not (Address.StartsWithF("http://") OrElse Address.StartsWithF("https://") OrElse Address.StartsWithF("socks")) Then
                    Address = "http://" & Address
                End If
                    _CustomAddress = New Uri(Address)
                    _CustomProxy.Address = _CustomAddress
                Catch ex As Exception
                    Logger.Warn(ex, "解析自定义代理地址失败：" & CustomAddress)
                    _CustomAddress = Nothing
                    _CustomProxy.Address = Nothing
                End Try
                If CustomUsername.Any Then
                    _CustomCredentials = New NetworkCredential(CustomUsername, CustomPassword)
                    _CustomProxy.Credentials = _CustomCredentials
                End If
            Else
                _CustomAddress = Nothing
                _CustomProxy.Address = Nothing
            End If
        End SyncLock
    End Sub

    ''' <summary>
    ''' 重新获取系统代理（Windows 动态读取，一般无需手动调用）。
    ''' </summary>
    Public Sub RefreshSystemProxy()
        SyncLock _Lock
            Try
                _SystemProxy = WebRequest.GetSystemWebProxy()
            Catch ex As Exception
                Logger.Warn(ex, "获取系统代理失败")
            End Try
        End SyncLock
    End Sub

    ''' <summary>
    ''' 当前代理模式。
    ''' </summary>
    Public ReadOnly Property Mode As ProxyMode
        Get
            SyncLock _Lock
                Return _Mode
            End SyncLock
        End Get
    End Property

    ''' <summary>
    ''' 获取代理地址。若无代理则返回 Nothing。
    ''' 用于向游戏 JVM 传递 -Dhttp.proxyHost 等参数。
    ''' </summary>
    Public Function GetProxyAddress() As Uri
        SyncLock _Lock
            Select Case _Mode
                Case ProxyMode.NoProxy
                    Return Nothing
                Case ProxyMode.CustomProxy
                    Return _CustomProxy.Address
                Case Else
                    Try
                        Dim Uri As New Uri("https://tcl.example/")
                        Return _SystemProxy.GetProxy(Uri)
                    Catch ex As Exception
                        Logger.Warn(ex, "读取系统代理地址失败")
                        Return Nothing
                    End Try
            End Select
        End SyncLock
    End Function

    ''' <summary>
    ''' 获取自定义代理凭据。仅自定义模式下可能非 Nothing。
    ''' </summary>
    Public Property Credentials As ICredentials Implements IWebProxy.Credentials
        Get
            SyncLock _Lock
                Return _CustomCredentials
            End SyncLock
        End Get
        Set(Value As ICredentials)
            SyncLock _Lock
                _CustomCredentials = Value
                _CustomProxy.Credentials = Value
            End SyncLock
        End Set
    End Property

    Public Function GetProxy(uri As Uri) As Uri Implements IWebProxy.GetProxy
        SyncLock _Lock
            Select Case _Mode
                Case ProxyMode.NoProxy
                    Return Nothing
                Case ProxyMode.CustomProxy
                    If _CustomProxy.Address Is Nothing Then Return Nothing '未配置有效地址时视为无代理，避免 WebProxy 抛异常
                    Return _CustomProxy.GetProxy(uri)
                Case Else
                    Try
                        Return _SystemProxy?.GetProxy(uri)
                    Catch ex As Exception
                        Logger.Warn(ex, "通过系统代理请求失败")
                        Return Nothing
                    End Try
            End Select
        End SyncLock
    End Function

    Public Function IsBypassed(uri As Uri) As Boolean Implements IWebProxy.IsBypassed
        SyncLock _Lock
            Select Case _Mode
                Case ProxyMode.NoProxy
                    Return True
                Case ProxyMode.CustomProxy
                    If _CustomProxy.Address Is Nothing Then Return True
                    Return _CustomProxy.IsBypassed(uri)
                Case Else
                    Try
                        Return _SystemProxy?.IsBypassed(uri)
                    Catch ex As Exception
                        Return True
                    End Try
            End Select
        End SyncLock
    End Function

End Class
