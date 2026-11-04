Imports Newtonsoft.Json.Linq
Imports System.Windows.Media.Imaging

Public Class PageOtherNews

    Private Const NewsApi As String = "https://net-secondary.web.minecraft-services.net/api/v1.0/zh-cn/search?pageSize=24&sortType=Recent&category=News&newsOnly=true&page=1"
    Private IsLoad As Boolean = False
    Private Loading As Boolean = False

    Private Sub PageOtherNews_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If IsLoad Then Return
        IsLoad = True
        Reload()
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRefresh.Click
        Reload()
    End Sub

    Public Sub Reload()
        If Loading Then Return
        Loading = True
        LabTip.Text = ModLang.L("正在获取 Minecraft 官方新闻……")
        LabTip.Visibility = Visibility.Visible
        PanNews.Children.Clear()
        Task.Run(Sub()
                     Try
                         Dim Raw = NetRequestByLoader(NewsApi, 20000, IsJson:=True)
                         Dim Json = JObject.Parse(Raw)
                         Dim Results = Json("result")?("results")
                         If Results Is Nothing Then Throw New Exception("新闻数据格式异常")
                         ModBase.RunInUi(Sub()
                                             RenderNews(Results)
                                             LabTip.Visibility = Visibility.Collapsed
                                         End Sub)
                     Catch ex As Exception
                         Logger.Error(ex, "获取 Minecraft 新闻失败")
                         ModBase.RunInUi(Sub()
                                             LabTip.Text = ModLang.L("获取新闻失败，请检查网络后重试。")
                                             LabTip.Visibility = Visibility.Visible
                                         End Sub)
                     Finally
                         Loading = False
                     End Try
                 End Sub)
    End Sub

    Private Sub RenderNews(Results As JToken)
        PanNews.Children.Clear()
        For Each Item In Results
            Try
                PanNews.Children.Add(CreateNewsCard(Item))
            Catch ex As Exception
                Logger.Error(ex, "创建新闻卡片失败")
            End Try
        Next
        If PanNews.Children.Count = 0 Then
            LabTip.Text = ModLang.L("暂时没有新闻。")
            LabTip.Visibility = Visibility.Visible
        End If
    End Sub

    Private Function CreateNewsCard(Item As JToken) As MyCard
        Dim Title = If(Item("title")?.ToString(), "Minecraft 新闻")
        Dim Description = If(Item("description")?.ToString(), "")
        Dim ImageUrl = If(Item("image")?.ToString(), "")
        Dim Url = If(Item("url")?.ToString(), "")
        Dim TimeValue As Long = 0
        Long.TryParse(If(Item("time")?.ToString(), "0"), TimeValue)
        Dim PublishText = ""
        If TimeValue > 0 Then
            PublishText = DateTimeOffset.FromUnixTimeSeconds(TimeValue).LocalDateTime.ToString("yyyy-MM-dd")
        End If

        Dim Card As New MyCard With {
            .Width = 340,
            .Margin = New Thickness(7),
            .Cursor = Cursors.Hand,
            .ToolTip = Description
        }
        Dim Grid As New Grid
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = New GridLength(160)})
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        Card.Children.Add(Grid)

        If Not String.IsNullOrEmpty(ImageUrl) Then
            Try
                Dim Bitmap As New BitmapImage()
                Bitmap.BeginInit()
                Bitmap.UriSource = New Uri(ImageUrl)
                Bitmap.CacheOption = BitmapCacheOption.OnLoad
                Bitmap.EndInit()
                Grid.Children.Add(New Image With {.Source = Bitmap, .Stretch = Stretch.UniformToFill})
            Catch ex As Exception
                Logger.Warn(ex, "加载新闻缩略图失败")
            End Try
        End If

        Dim PanText As New StackPanel With {.Margin = New Thickness(14, 10, 14, 12)}
        Grid.SetRow(PanText, 1)
        Grid.Children.Add(PanText)
        PanText.Children.Add(New TextBlock With {
            .Text = Title,
            .FontSize = 15,
            .FontWeight = FontWeights.Bold,
            .TextWrapping = TextWrapping.Wrap,
            .MaxHeight = 42,
            .TextTrimming = TextTrimming.CharacterEllipsis
        })
        If Not String.IsNullOrEmpty(PublishText) Then
            PanText.Children.Add(New TextBlock With {
                .Text = PublishText,
                .Opacity = 0.6,
                .FontSize = 11,
                .Margin = New Thickness(0, 4, 0, 0)
            })
        End If
        If Not String.IsNullOrEmpty(Description) Then
            PanText.Children.Add(New TextBlock With {
                .Text = Description,
                .Opacity = 0.75,
                .FontSize = 12,
                .Margin = New Thickness(0, 5, 0, 0),
                .TextWrapping = TextWrapping.Wrap,
                .MaxHeight = 38,
                .TextTrimming = TextTrimming.CharacterEllipsis
            })
        End If

        If Not String.IsNullOrEmpty(Url) Then
            AddHandler Card.MouseLeftButtonUp, Sub(sender As Object, e As MouseButtonEventArgs) OpenWebsite(Url)
        End If
        Card.Opacity = 0
        AniStart(AaOpacity(Card, 1, 200), "News Fade " & GetUuid())
        Return Card
    End Function

End Class
