Public Class PageDownloadFavorites

    Private IsLoad As Boolean = False

    Private Sub PageDownloadFavorites_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If IsLoad Then Return
        IsLoad = True
        ModResourceFavorites.LoadFavorites()
        Reload()
    End Sub

    Public Sub Reload()
        ModResourceFavorites.LoadFavorites()
        PanFavorites.Children.Clear()
        If ModResourceFavorites.Favorites.Count = 0 Then
            PanNoFav.Visibility = Visibility.Visible
            PanContent.Visibility = Visibility.Collapsed
            Return
        End If
        PanNoFav.Visibility = Visibility.Collapsed
        PanContent.Visibility = Visibility.Visible
        For Each Favorite In ModResourceFavorites.Favorites.ToList()
            PanFavorites.Children.Add(CreateCard(Favorite))
        Next
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRefresh.Click
        Reload()
        Hint(ModLang.L("收藏夹已刷新！"), HintType.Green)
    End Sub

    Private Function GetTypeName(Favorite As ModResourceFavorites.ResourceFavorite) As String
        Return ResourceKit.ShortName(CType(Favorite.Types, ResourceTypes))
    End Function

    Private Function GetTypeIcon(Favorite As ModResourceFavorites.ResourceFavorite) As String
        Return ResourceKit.Icon(CType(Favorite.Types, ResourceTypes))
    End Function

    Private Function CreateCard(Favorite As ModResourceFavorites.ResourceFavorite) As MyCard
        Dim Card As New MyCard With {
            .Margin = New Thickness(15, 0, 15, 12),
            .Tag = Favorite
        }
        Dim Grid As New Grid With {.Margin = New Thickness(20, 15, 20, 15)}
        Grid.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(58)})
        Grid.ColumnDefinitions.Add(New ColumnDefinition)
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        Card.Children.Add(Grid)

        Dim IconHost As New Grid With {
            .Width = 48, .Height = 48,
            .VerticalAlignment = VerticalAlignment.Top
        }
        IconHost.Children.Add(New Global.TClUI.Controls.SvgIcon.SvgIcon With {
            .Icon = GetTypeIcon(Favorite),
            .Width = 32, .Height = 32,
            .HorizontalAlignment = HorizontalAlignment.Center,
            .VerticalAlignment = VerticalAlignment.Center,
            .IconBrush = CType(FindResource("ColorBrush2"), Brush)
        })
        Grid.SetColumn(IconHost, 0)
        Grid.SetRowSpan(IconHost, 2)
        Grid.Children.Add(IconHost)

        Dim PanInfo As New StackPanel With {.Margin = New Thickness(6, 0, 0, 0)}
        Grid.SetColumn(PanInfo, 1)
        Grid.Children.Add(PanInfo)
        PanInfo.Children.Add(New TextBlock With {
            .Text = Favorite.Name,
            .FontSize = 16,
            .FontWeight = FontWeights.Bold,
            .TextTrimming = TextTrimming.CharacterEllipsis
        })
        PanInfo.Children.Add(New TextBlock With {
            .Text = $"{GetTypeName(Favorite)} · {CType(Favorite.Platform, ResourcePlatforms)}",
            .Opacity = 0.7,
            .Margin = New Thickness(0, 3, 0, 0)
        })
        If Not String.IsNullOrWhiteSpace(Favorite.Description) Then
            PanInfo.Children.Add(New TextBlock With {
                .Text = Favorite.Description,
                .Opacity = 0.75,
                .Margin = New Thickness(0, 5, 0, 0),
                .TextWrapping = TextWrapping.Wrap,
                .MaxHeight = 40,
                .TextTrimming = TextTrimming.CharacterEllipsis
            })
        End If

        Dim PanBtn As New StackPanel With {
            .Orientation = Orientation.Horizontal,
            .HorizontalAlignment = HorizontalAlignment.Right,
            .Margin = New Thickness(0, 10, 0, 0)
        }
        Grid.SetColumn(PanBtn, 1)
        Grid.SetRow(PanBtn, 1)
        Grid.Children.Add(PanBtn)

        Dim BtnDetail As New MyIconTextButton With {.Text = ModLang.L("详情"), .LogoScale = 0.8, .SvgIcon = "lucide/info"}
        AddHandler BtnDetail.Click, Sub(sender As Object, e As RouteEventArgs) OpenDetail(Favorite)
        PanBtn.Children.Add(BtnDetail)

        Dim BtnWeb As New MyIconTextButton With {.Text = ModLang.L("打开网页"), .LogoScale = 0.8, .SvgIcon = "lucide/external-link"}
        AddHandler BtnWeb.Click, Sub(sender As Object, e As RouteEventArgs)
                                      If Not String.IsNullOrEmpty(Favorite.Website) Then OpenWebsite(Favorite.Website)
                                  End Sub
        PanBtn.Children.Add(BtnWeb)

        Dim BtnRemove As New MyIconTextButton With {.Text = ModLang.L("取消收藏"), .LogoScale = 0.8, .SvgIcon = "lucide/trash-2"}
        AddHandler BtnRemove.Click, Sub(sender As Object, e As RouteEventArgs)
                                        ModResourceFavorites.FavoritesRemove(Favorite)
                                        PanFavorites.Children.Remove(Card)
                                        If ModResourceFavorites.Favorites.Count = 0 Then Reload()
                                        Hint(ModLang.L("已取消收藏！"), HintType.Green)
                                    End Sub
        PanBtn.Children.Add(BtnRemove)

        Card.Opacity = 0
        AniStart(AaOpacity(Card, 1, 200), "Favorites Fade " & GetUuid())
        Return Card
    End Function

    Private Sub OpenDetail(Favorite As ModResourceFavorites.ResourceFavorite)
        Task.Run(Sub()
                     Try
                         Dim Project = ResourceProject.FromProjectId(Favorite.Id, CType(Favorite.Platform, ResourcePlatforms))
                         If Project Is Nothing Then
                             ModBase.RunInUi(Sub() Hint(ModLang.L("无法获取该资源的信息！"), HintType.Red))
                             Return
                         End If
                         ModBase.RunInUi(Sub()
                                             FrmMain.PageChange(New FormMain.PageStackData With {
                                                 .Page = FormMain.PageType.ResourceDetail,
                                                 .Additional = {Project, New List(Of String), "", ModLoaders.None, CType(Favorite.Types, ResourceTypes)}
                                             })
                                         End Sub)
                     Catch ex As Exception
                         Logger.Error(ex, "打开收藏资源详情失败", LogBehavior.Toast)
                     End Try
                 End Sub)
    End Sub

End Class
