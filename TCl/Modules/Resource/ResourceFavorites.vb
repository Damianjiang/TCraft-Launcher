Imports Newtonsoft.Json

''' <summary>
''' 资源收藏夹的本地存储。
''' </summary>
Public Module ModResourceFavorites

    Public Class ResourceFavorite
        Public Platform As Integer
        Public Id As String = ""
        Public Slug As String = ""
        Public Name As String = ""
        Public Description As String = ""
        Public Website As String = ""
        Public LogoUrl As String = ""
        Public Types As Integer
    End Class

    Public ReadOnly Favorites As New List(Of ResourceFavorite)

    Private ReadOnly Property FilePath As String
        Get
            Return Paths.Base & "TCL\ResourceFavorites.json"
        End Get
    End Property

    Public Sub LoadFavorites()
        Favorites.Clear()
        Try
            If Not File.Exists(FilePath) Then Return
            Dim Content = File.ReadAllText(FilePath, Text.Encoding.UTF8)
            If String.IsNullOrWhiteSpace(Content) Then Return
            Dim List = JsonConvert.DeserializeObject(Of List(Of ResourceFavorite))(Content)
            If List IsNot Nothing Then Favorites.AddRange(List)
        Catch ex As Exception
            Logger.Error(ex, "读取资源收藏夹失败")
        End Try
    End Sub

    Public Sub SaveFavorites()
        Try
            Dim Folder = IO.Path.GetDirectoryName(FilePath)
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            File.WriteAllText(FilePath, JsonConvert.SerializeObject(Favorites, Formatting.Indented), Text.Encoding.UTF8)
        Catch ex As Exception
            Logger.Error(ex, "保存资源收藏夹失败", LogBehavior.Toast)
        End Try
    End Sub

    Public Function FavoritesContains(Project As ResourceProject) As Boolean
        Return Favorites.Any(Function(f) f.Id = Project.Id AndAlso f.Platform = CInt(Project.Platform))
    End Function

    Public Sub FavoritesAdd(Project As ResourceProject)
        If FavoritesContains(Project) Then Return
        Favorites.Add(New ResourceFavorite With {
            .Platform = CInt(Project.Platform),
            .Id = Project.Id,
            .Slug = Project.Slug,
            .Name = Project.TranslatedName,
            .Description = Project.Description,
            .Website = Project.Website,
            .LogoUrl = If(Project.LogoUrl, ""),
            .Types = CInt(Project.Types)
        })
        SaveFavorites()
    End Sub

    Public Sub FavoritesRemove(Project As ResourceProject)
        Favorites.RemoveAll(Function(f) f.Id = Project.Id AndAlso f.Platform = CInt(Project.Platform))
        SaveFavorites()
    End Sub

    Public Sub FavoritesRemove(Favorite As ResourceFavorite)
        Favorites.Remove(Favorite)
        SaveFavorites()
    End Sub

End Module
