' ============================================================================
'  TCl 自制 JSON 引擎（彻底替代第三方 Newtonsoft.Json 二进制，零外部依赖）
'  命名空间布局与 Newtonsoft 一致，调用点零改动：
'    Newtonsoft.Json        → Formatting / JTokenType / JsonConvert
'    Newtonsoft.Json.Linq   → JToken / JValue / JContainer / JObject / JArray / JProperty
'  支持：默认字符串/整数索引器、.Value<>/.ToObject<>、Parse/FromObject、序列化、
'        反射式 POCO 双向映射（枚举/日期/列表/数组/嵌套对象）、
'        .Children(基类出 JToken / JObject 出 JProperty)/.Properties()/Linq、
'        JToken 到 String/数值/布尔/日期 的隐式转换。
'  由 TCl 团队自行实现，不依赖任何外部程序集。
' ============================================================================
Option Explicit On

Imports System.Text
Imports System.Collections.Generic
Imports System.Globalization
Imports System.Reflection

Namespace Newtonsoft.Json

    ''' <summary>JSON 序列化/反序列化开关。</summary>
    Public Enum Formatting
        [None]
        Indented
    End Enum

    ''' <summary>JSON 节点类型。</summary>
    Public Enum JTokenType
        [None]
        [Object]
        [Array]
        [String]
        [Integer]
        [Float]
        [Boolean]
        [Null]
        [Date]
        [Bytes]
        [Undefined]
        [Raw]
    End Enum

    ''' <summary>JSON 序列化/反序列化入口（对齐 Newtonsoft.Json.JsonConvert）。</summary>
    Public Class JsonConvert

        Public Shared Function DeserializeObject(Of T)(json As String) As T
            Dim token As Newtonsoft.Json.Linq.JToken = Newtonsoft.Json.Linq.JToken.Parse(json)
            If token Is Nothing Then Return Nothing
            Return Newtonsoft.Json.Linq.TClJsonCast.ToT(Of T)(token)
        End Function

        Public Shared Function DeserializeObject(json As String) As Object
            Dim token As Newtonsoft.Json.Linq.JToken = Newtonsoft.Json.Linq.JToken.Parse(json)
            If token Is Nothing Then Return Nothing
            Return Newtonsoft.Json.Linq.TClJsonCast.ToObjectCore(token)
        End Function

        Public Shared Function SerializeObject(obj As Object) As String
            Return SerializeObject(obj, Formatting.[None])
        End Function

        Public Shared Function SerializeObject(obj As Object, formatting As Formatting) As String
            Dim token As Newtonsoft.Json.Linq.JToken = Newtonsoft.Json.Linq.TClJsonCast.FromObject(obj)
            Return token.ToJson(formatting)
        End Function

    End Class

End Namespace

Namespace Newtonsoft.Json.Linq

    ' ==================================================================
    '  节点基类（双接口：泛型 + 非泛型 IEnumerable，供 For Each 协变窄化与 Linq）
    ' ==================================================================
    Public MustInherit Class JToken : Implements IEnumerable(Of JToken), System.Collections.IEnumerable

        Public Property Parent As JToken

        Public MustOverride ReadOnly Property [Type] As Newtonsoft.Json.JTokenType
        Public MustOverride Function GetCoreValue() As Object

        ' ---------- 子项枚举（协变：容器返回真实子项，叶子返回空） ----------
        Protected Overridable Function InternalChildren() As List(Of JToken)
            Return New List(Of JToken)()
        End Function
        Public Function GetEnumerator() As IEnumerator(Of JToken) Implements IEnumerable(Of JToken).GetEnumerator
            Return InternalChildren().GetEnumerator()
        End Function
        Public Function GetNonEnumerator() As System.Collections.IEnumerator Implements System.Collections.IEnumerable.GetEnumerator
            Return InternalChildren().GetEnumerator()
        End Function

        ' ---------- 默认索引器 ----------
        Public Overridable Default Property Item(key As String) As JToken
            Get
                Return Nothing
            End Get
            Set
            End Set
        End Property
        Public Overridable Default Property Item(index As Integer) As JToken
            Get
                Return Nothing
            End Get
            Set
            End Set
        End Property

        ' ---------- 序列化 ----------
        Public MustOverride Function ToJson(formatting As Newtonsoft.Json.Formatting) As String
        Public Overrides Function ToString() As String
            Return TClJsonCast.ScalarText(GetCoreValue())
        End Function

        ' ---------- 取值 ----------
        Public MustOverride Function ToObject() As Object
        Public MustOverride Function ToObject(Of T)() As T
        Public Function Value(Of T)() As T
            Return ToObject(Of T)()
        End Function

        ' ---------- 深度克隆 / 移除 ----------
        Public MustOverride Function CloneNode() As JToken
        Public Function DeepClone() As JToken
            Return CloneNode()
        End Function
        Public Sub Remove()
            If Parent Is Nothing Then Return
            Parent.RemoveChild(Me)
        End Sub
        Public MustOverride Sub RemoveChild(child As JToken)

        ' ---------- 容器通用 ----------
        Public Overridable ReadOnly Property [Count] As Integer
            Get
                Return InternalChildren().Count
            End Get
        End Property
        Public Overridable Function HasValues() As Boolean
            Return InternalChildren().Count > 0
        End Function

        ' 子节点（基类出 JToken；JObject 覆写出 JProperty，供 Linq 匹配 JProperty lambda）
        Public Overridable ReadOnly Property Children As IEnumerable(Of JToken)
            Get
                Return InternalChildren()
            End Get
        End Property

        ' 便捷：类型筛选 / 求值（供 Linq 与基类迭代）
        Public Function ChildrenOfT(Of T)() As List(Of T)
            Dim l As New List(Of T)()
            For Each c In Me
                If TypeOf c Is T Then
                    Dim o As Object = c
                    l.Add(DirectCast(o, T))
                End If
            Next
            Return l
        End Function
        Public Function OfType(Of T)() As List(Of T)
            Return ChildrenOfT(Of T)()
        End Function
        Public Function Any() As Boolean
            For Each d In Me
                Return True
            Next
            Return False
        End Function
        Public Function First() As JToken
            For Each c In Me
                Return c
            Next
            Return Nothing
        End Function
        Public Function [Last]() As JToken
            Dim result As JToken = Nothing
            For Each c In Me
                result = c
            Next
            Return result
        End Function
        Public Function ToList() As List(Of JToken)
            Dim l As New List(Of JToken)()
            For Each c In Me
                l.Add(c)
            Next
            Return l
        End Function

        ' ---------- 工厂 ----------
        Public Shared Function Parse(text As String) As JToken
            Return TClJsonParse.Parse(text)
        End Function
        Public Shared Function FromObject(obj As Object) As JToken
            Return TClJsonCast.FromObject(obj)
        End Function

        ' ---------- 隐式转换（JToken → 基础类型），满足 VB 直接赋值 ----------
        Public Shared Widening Operator CType(t As JToken) As String
            If t Is Nothing Then Return ""
            Dim v = t.GetCoreValue()
            If v Is Nothing Then Return ""
            Return TClJsonCast.ScalarText(v)
        End Operator
        Public Shared Widening Operator CType(t As JToken) As Integer
            Return TClJsonCast.CastCore(Of Integer)(t.GetCoreValue())
        End Operator
        Public Shared Widening Operator CType(t As JToken) As Long
            Return TClJsonCast.CastCore(Of Long)(t.GetCoreValue())
        End Operator
        Public Shared Widening Operator CType(t As JToken) As Double
            Return TClJsonCast.CastCore(Of Double)(t.GetCoreValue())
        End Operator
        Public Shared Widening Operator CType(t As JToken) As Boolean
            Return TClJsonCast.CastCore(Of Boolean)(t.GetCoreValue())
        End Operator

    End Class

    ' ---------- JValue（叶子） ----------
    Public Class JValue : Inherits JToken

        Private _value As Object
        Private _type As Newtonsoft.Json.JTokenType = Newtonsoft.Json.JTokenType.[Null]

        Public Sub New()
        End Sub
        Public Sub New(value As String)
            _value = value
            _type = Newtonsoft.Json.JTokenType.[String]
        End Sub
        Public Sub New(value As Boolean)
            _value = value
            _type = Newtonsoft.Json.JTokenType.[Boolean]
        End Sub
        Public Sub New(value As Integer)
            _value = value
            _type = Newtonsoft.Json.JTokenType.[Integer]
        End Sub
        Public Sub New(value As Long)
            _value = value
            _type = Newtonsoft.Json.JTokenType.[Integer]
        End Sub
        Public Sub New(value As Double)
            _value = value
            _type = Newtonsoft.Json.JTokenType.[Float]
        End Sub
        Public Sub New(value As Date)
            _value = value
            _type = Newtonsoft.Json.JTokenType.[Date]
        End Sub
        Public Sub New(value As Object)
            _value = value
            InferCore(value)
        End Sub

        Private Sub InferCore(value As Object)
            _value = value
            If value Is Nothing Then
                _type = Newtonsoft.Json.JTokenType.[Null]
            ElseIf TypeOf value Is String Then
                _type = Newtonsoft.Json.JTokenType.[String]
            ElseIf TypeOf value Is Boolean Then
                _type = Newtonsoft.Json.JTokenType.[Boolean]
            ElseIf TypeOf value Is Integer OrElse TypeOf value Is Long OrElse TypeOf value Is Short OrElse TypeOf value Is Byte OrElse TypeOf value Is SByte OrElse TypeOf value Is UInteger OrElse TypeOf value Is ULong Then
                _type = Newtonsoft.Json.JTokenType.[Integer]
            ElseIf TypeOf value Is Double OrElse TypeOf value Is Single OrElse TypeOf value Is Decimal OrElse TypeOf value Is Char Then
                _type = Newtonsoft.Json.JTokenType.[Float]
            ElseIf TypeOf value Is Date OrElse TypeOf value Is DateTime Then
                _type = Newtonsoft.Json.JTokenType.[Date]
            ElseIf TypeOf value Is Byte() Then
                _type = Newtonsoft.Json.JTokenType.[Bytes]
            Else
                _type = Newtonsoft.Json.JTokenType.[Raw]
            End If
        End Sub

        Public Overrides ReadOnly Property [Type] As Newtonsoft.Json.JTokenType
            Get
                Return _type
            End Get
        End Property
        Public Overrides Function GetCoreValue() As Object
            Return _value
        End Function
        Public Overrides Sub RemoveChild(child As JToken)
        End Sub
        Public Overrides Function ToJson(formatting As Newtonsoft.Json.Formatting) As String
            Return TClJsonCast.ScalarText(_value)
        End Function
        Public Overrides Function ToObject() As Object
            Return _value
        End Function
        Public Overrides Function ToObject(Of T)() As T
            Return TClJsonCast.ToT(Of T)(_value)
        End Function
        Public Overrides Function CloneNode() As JToken
            Return New JValue(_value)
        End Function

        Public Overrides Default Property Item(key As String) As JToken
            Get
                Return Nothing
            End Get
            Set
            End Set
        End Property
        Public Overrides Default Property Item(index As Integer) As JToken
            Get
                Return Nothing
            End Get
            Set
            End Set
        End Property

        Public Shared Function CreateNull() As JValue
            Return New JValue()
        End Function
        Public Shared Function CreateString(v As String) As JValue
            Return New JValue(v)
        End Function

    End Class

    ' ---------- JContainer ----------
    Public MustInherit Class JContainer : Inherits JToken

        Private _children As New List(Of JToken)()

        Protected Overrides Function InternalChildren() As List(Of JToken)
            Return _children
        End Function

        Public Overrides ReadOnly Property [Count] As Integer
            Get
                Return _children.Count
            End Get
        End Property
        Public Overrides Function HasValues() As Boolean
            Return _children.Count > 0
        End Function
        Public Overrides ReadOnly Property Children As IEnumerable(Of JToken)
            Get
                Return _children
            End Get
        End Property

        Public Sub AddChild(child As JToken)
            If child Is Nothing Then Return
            child.Parent = Me
            _children.Add(child)
        End Sub
        Public Overrides Sub RemoveChild(child As JToken)
            _children.Remove(child)
        End Sub

        ' 索引器落到 child
        Public Overrides Default Property Item(key As String) As JToken
            Get
                Return GetStringChild(key)
            End Get
            Set
                SetStringChild(key, Value)
            End Set
        End Property
        Public Overrides Default Property Item(index As Integer) As JToken
            Get
                If index < 0 OrElse index >= _children.Count Then Return Nothing
                Return _children(index)
            End Get
            Set
                If index >= 0 AndAlso index < _children.Count Then
                    Value.Parent = Me
                    _children(index) = Value
                End If
            End Set
        End Property

        Protected MustOverride Function GetStringChild(key As String) As JToken
        Protected MustOverride Sub SetStringChild(key As String, value As JToken)

        Friend Function GetChildrenList() As List(Of JToken)
            Return _children
        End Function

    End Class

    ' ---------- JProperty（JObject 的子项；本身是 JToken） ----------
    Public Class JProperty : Inherits JToken

        Public Property Name As String
        Public Shadows Property Value As JToken

        Public Sub New()
        End Sub
        Public Sub New(name As String, value As JToken)
            Me.Name = name
            Me.Value = value
        End Sub

        Public Overrides ReadOnly Property [Type] As Newtonsoft.Json.JTokenType
            Get
                Return If(Me.Value Is Nothing, Newtonsoft.Json.JTokenType.[Null], Me.Value.[Type])
            End Get
        End Property
        Public Overrides Function GetCoreValue() As Object
            Return Me
        End Function
        Public Overrides Sub RemoveChild(child As JToken)
        End Sub
        Public Overrides Function ToObject() As Object
            Return Me
        End Function
        Public Overrides Function ToObject(Of T)() As T
            Return If(Me.Value Is Nothing, TClJsonCast.DefaultOf(Of T)(), Me.Value.ToObject(Of T)())
        End Function
        Public Overrides Function ToJson(formatting As Newtonsoft.Json.Formatting) As String
            Return JsonWriter.EscapedString(Name) + ": " + If(Me.Value Is Nothing, "null", Me.Value.ToJson(formatting))
        End Function
        Public Overrides Function CloneNode() As JToken
            Return New JProperty(Name, If(Me.Value Is Nothing, Nothing, Me.Value.CloneNode()))
        End Function
        Public Overrides Function ToString() As String
            Return Name
        End Function

        ' 转发到 Value（"prop.name" 语义）
        Public Overrides Default Property Item(key As String) As JToken
            Get
                Return If(Me.Value Is Nothing, Nothing, Me.Value.Item(key))
            End Get
            Set
                If Me.Value IsNot Nothing Then Me.Value.Item(key) = value
            End Set
        End Property
        Public Overrides Default Property Item(index As Integer) As JToken
            Get
                Return If(Me.Value Is Nothing, Nothing, Me.Value.Item(index))
            End Get
            Set
                If Me.Value IsNot Nothing Then Me.Value.Item(index) = value
            End Set
        End Property

    End Class

    ' ---------- JObject ----------
    Public Class JObject : Inherits JContainer

        Public Sub New()
        End Sub
        Public Sub New(source As Object)
            If source Is Nothing Then Return
            If TypeOf source Is JObject Then
                For Each p In CType(source, JObject).GetPropertiesList()
                    AddChild(New JProperty(p.Name, If(p.Value Is Nothing, Nothing, p.Value.CloneNode())))
                Next
                Return
            End If
            TClJsonCast.FillObject(Me, source)
        End Sub

        ' 多行构造器（承接 New JObject({New JProperty(...), ...}) 数组初始化）
        Public Sub New(items As JProperty())
            If items Is Nothing Then Return
            For Each p In items
                AddChild(New JProperty(p.Name, If(p.Value Is Nothing, Nothing, p.Value.CloneNode())))
            Next
        End Sub

        Public Overrides ReadOnly Property [Type] As Newtonsoft.Json.JTokenType
            Get
                Return Newtonsoft.Json.JTokenType.[Object]
            End Get
        End Property
        Public Overrides Function GetCoreValue() As Object
            Return Me
        End Function

        ' 字符串子项：按 key 取 JProperty.Value
        Protected Overrides Function GetStringChild(key As String) As JToken
            For Each c In GetChildrenList()
                If TypeOf c Is JProperty AndAlso CType(c, JProperty).Name = key Then
                    Return CType(c, JProperty).Value
                End If
            Next
            Return Nothing
        End Function
        Protected Overrides Sub SetStringChild(key As String, value As JToken)
            For i As Integer = GetChildrenList().Count - 1 To 0 Step -1
                Dim c As JToken = GetChildrenList()(i)
                If TypeOf c Is JProperty AndAlso CType(c, JProperty).Name = key Then
                    GetChildrenList()(i) = New JProperty(key, value)
                    Return
                End If
            Next
            If value Is Nothing Then Return
            AddChild(New JProperty(key, value))
        End Sub

        Public Function GetPropertiesList() As List(Of JProperty)
            Dim l As New List(Of JProperty)()
            For Each c In GetChildrenList()
                If TypeOf c Is JProperty Then l.Add(CType(c, JProperty))
            Next
            Return l
        End Function

        ' ToObject（JObject 返回自身；JContainer/JToken 的 MustOverride）
        Public Overrides Function ToObject() As Object
            Return Me
        End Function
        Public Overrides Function ToObject(Of T)() As T
            If GetType(T) = GetType(JToken) OrElse GetType(T) = GetType(JObject) OrElse GetType(T) = GetType(JProperty) Then
                Dim o As Object = Me : Return DirectCast(o, T)
            End If
            Return TClJsonCast.ToT(Of T)(Me)
        End Function

        ' Properties()：无参方法，返回 List（支持 .Count / (i) / Linq / 带与不带括号；经 vb_noarg.vb 验证）
        Public Function Properties() As List(Of JProperty)
            Return GetPropertiesList()
        End Function
        Public Function GetProperty(name As String) As JProperty
            Return GetProp(name)
        End Function
        Public Function ContainsKey(key As String) As Boolean
            Return Not IsNothing(GetProp(key))
        End Function
        Private Function GetProp(key As String) As JProperty
            For Each c In GetChildrenList()
                If TypeOf c Is JProperty AndAlso CType(c, JProperty).Name = key Then Return CType(c, JProperty)
            Next
            Return Nothing
        End Function
        Public Function TryGetValue(key As String, ByRef value As JToken) As Boolean
            Dim p = GetProp(key)
            value = If(p Is Nothing, Nothing, p.Value)
            Return p IsNot Nothing
        End Function
        Public Overloads Sub Remove(key As String)
            For i As Integer = GetChildrenList().Count - 1 To 0 Step -1
                If TypeOf GetChildrenList()(i) Is JProperty AndAlso CType(GetChildrenList()(i), JProperty).Name = key Then
                    GetChildrenList().RemoveAt(i)
                    Exit For
                End If
            Next
        End Sub
        Public Sub RemoveProperty(prop As JProperty)
            GetChildrenList().Remove(prop)
        End Sub

        ' 取值
        Public Overloads Function Value(Of T)(key As String) As T
            Dim p = GetProp(key)
            Return If(p Is Nothing OrElse p.Value Is Nothing, TClJsonCast.DefaultOf(Of T)(), p.Value.ToObject(Of T)())
        End Function
        Public Overloads Function Value(Of T)(key As String, defaultValue As T) As T
            Dim p = GetProp(key)
            If p IsNot Nothing AndAlso p.Value IsNot Nothing Then Return p.Value.ToObject(Of T)()
            Return defaultValue
        End Function

        ' 合并
        Public Sub Merge(other As JToken)
            If other Is Nothing OrElse Not TypeOf other Is JObject Then Return
            Dim o As JObject = CType(other, JObject)
            For Each p In o.GetPropertiesList()
                Dim cur As JProperty = Nothing
                For Each c In GetChildrenList()
                    If TypeOf c Is JProperty AndAlso CType(c, JProperty).Name = p.Name Then cur = CType(c, JProperty)
                Next
                If cur IsNot Nothing AndAlso TypeOf cur.Value Is JObject AndAlso TypeOf p.Value Is JObject Then
                    CType(cur.Value, JObject).Merge(p.Value)
                Else
                    SetStringChild(p.Name, If(p.Value Is Nothing, Nothing, p.Value.CloneNode()))
                End If
            Next
        End Sub

        ' 覆写子项枚举：JObject 的 Children 以 JProperty 暴露（供 Linq .Any(Function(j As JProperty)) 与 For Each ... As JProperty）
        Public Shadows ReadOnly Property Children As IEnumerable(Of JProperty)
            Get
                Return GetPropertiesList()
            End Get
        End Property

        Public Overrides Function CloneNode() As JToken
            Return New JObject(Me)
        End Function
        Public Overrides Function ToJson(formatting As Newtonsoft.Json.Formatting) As String
            Return JObjectWriter.Write(Me, formatting)
        End Function

        ' 工厂
        Public Shared Overloads Function Parse(text As String) As JObject
            Dim t As JToken = TClJsonParse.Parse(text)
            Return If(TypeOf t Is JObject, CType(t, JObject), Nothing)
        End Function
        Public Shared Overloads Function FromObject(obj As Object) As JObject
            Return New JObject(obj)
        End Function

    End Class

    ' ---------- JArray ----------
    Public Class JArray : Inherits JContainer

        Public Sub New()
        End Sub
        Public Sub New(source As Object)
            If source Is Nothing Then Return
            If TypeOf source Is JArray Then
                For Each it In CType(source, JArray)
                    AddChild(If(it Is Nothing, Nothing, it.CloneNode()))
                Next
                Return
            End If
            TClJsonCast.FillArray(Me, source)
        End Sub

        Public Overrides ReadOnly Property [Type] As Newtonsoft.Json.JTokenType
            Get
                Return Newtonsoft.Json.JTokenType.[Array]
            End Get
        End Property
        Public Overrides Function GetCoreValue() As Object
            Return Me
        End Function

        ' 数组：按 key 在对象元素里查找（兼容 .Item("k") 语义）
        Protected Overrides Function GetStringChild(key As String) As JToken
            For Each c In GetChildrenList()
                If TypeOf c Is JObject Then
                    Dim p As JProperty = CType(c, JObject).GetProperty(key)
                    If p IsNot Nothing Then Return p.Value
                End If
            Next
            Return Nothing
        End Function
        Protected Overrides Sub SetStringChild(key As String, value As JToken)
        End Sub

        Public Sub Add(token As JToken)
            AddChild(token)
        End Sub
        Public Sub Add(value As Object)
            AddChild(TClJsonCast.FromObject(value))
        End Sub

        Public Overrides Function ToObject() As Object
            Dim l As New List(Of Object)()
            For Each c In GetChildrenList()
                l.Add(c.GetCoreValue())
            Next
            Return l
        End Function
        Public Overrides Function ToObject(Of T)() As T
            Return TClJsonCast.ToT(Of T)(Me)
        End Function
        Public Overrides Function CloneNode() As JToken
            Return New JArray(Me)
        End Function
        Public Overrides Function ToJson(formatting As Newtonsoft.Json.Formatting) As String
            Return JArrayWriter.Write(Me, formatting)
        End Function

        Public Shared Overloads Function Parse(text As String) As JArray
            Dim t As JToken = TClJsonParse.Parse(text)
            Return If(TypeOf t Is JArray, CType(t, JArray), Nothing)
        End Function
        Public Shared Overloads Function FromObject(obj As Object) As JArray
            Return New JArray(obj)
        End Function

    End Class

    ' ==================================================================
    '  解析器
    ' ==================================================================
    Public Class TClJsonParse

        Private _s As String
        Private _i As Integer

        Public Sub New(text As String)
            _s = text
            _i = 0
        End Sub

        Public Shared Function Parse(text As String) As JToken
            If String.IsNullOrWhiteSpace(text) Then Return Nothing
            Dim p As New TClJsonParse(text)
            p.SkipWs()
            Return p.ParseValue()
        End Function

        Private Sub SkipWs()
            While _i < _s.Length
                Select Case _s(_i)
                    Case " ", vbTab, vbCr, vbLf
                        _i += 1
                    Case Else
                        Exit While
                End Select
            End While
        End Sub

        Private Function ParseValue() As JToken
            SkipWs()
            If _i >= _s.Length Then Return Nothing
            Select Case _s(_i)
                Case "{"
                    Return ParseObject()
                Case "["
                    Return ParseArray()
                Case """", "'"
                    Return New JValue(ParseString())
                Case Else
                    Return ParseScalar()
            End Select
        End Function

        Private Function ParseObject() As JObject
            Dim o As New JObject()
            _i += 1
            SkipWs()
            If _i < _s.Length AndAlso _s(_i) = "}"c Then
                _i += 1
                Return o
            End If
            While True
                SkipWs()
                If _i >= _s.Length Then Throw New FormatException("JSON 对象未闭合")
                Dim key As String
                If _s(_i) = "}"c Then
                    _i += 1
                    Return o
                End If
                key = ParseString()
                SkipWs()
                If _i >= _s.Length OrElse _s(_i) <> ":"c Then Throw New FormatException(ModLang.L("JSON 对象缺少冒号：") & key)
                _i += 1
                Dim val As JToken = ParseValue()
                o.Item(key) = val
                SkipWs()
                If _i >= _s.Length Then Throw New FormatException("JSON 对象未闭合")
                Select Case _s(_i)
                    Case "}"
                        _i += 1
                        Return o
                    Case ","
                        _i += 1
                    Case Else
                        Throw New FormatException(ModLang.L("JSON 对象语法错误：") & _s(_i))
                End Select
            End While
            Return o
        End Function

        Private Function ParseArray() As JArray
            Dim a As New JArray()
            _i += 1
            SkipWs()
            If _i < _s.Length AndAlso _s(_i) = "]"c Then
                _i += 1
                Return a
            End If
            While True
                Dim val As JToken = ParseValue()
                a.Add(val)
                SkipWs()
                If _i >= _s.Length Then Throw New FormatException("JSON 数组未闭合")
                Select Case _s(_i)
                    Case "]"
                        _i += 1
                        Return a
                    Case ","
                        _i += 1
                    Case Else
                        Throw New FormatException("JSON 数组语法错误")
                End Select
            End While
            Return a
        End Function

        Private Function ParseString() As String
            Dim quote As Char = _s(_i)
            _i += 1
            Dim sb As New StringBuilder()
            Dim BSLASH As Char = ChrW(92) ' \
            Dim DQUOTE As Char = ChrW(34) ' "
            While _i < _s.Length
                Dim c = _s(_i)
                If c = quote Then
                    _i += 1
                    Return sb.ToString()
                ElseIf c = BSLASH Then
                    _i += 1
                    If _i >= _s.Length Then Throw New FormatException("JSON 字符串转义未闭合")
                    Select Case _s(_i)
                        Case ChrW(92)
                            sb.Append(BSLASH)
                            _i += 1
                        Case QUOTE
                            sb.Append(QUOTE)
                            _i += 1
                        Case ChrW(98) ' b
                            sb.Append(ChrW(8))
                            _i += 1
                        Case ChrW(102) ' f
                            sb.Append(ChrW(12))
                            _i += 1
                        Case ChrW(110) ' n
                            sb.Append(vbLf)
                            _i += 1
                        Case ChrW(114) ' r
                            sb.Append(vbCr)
                            _i += 1
                        Case ChrW(116) ' t
                            sb.Append(vbTab)
                            _i += 1
                        Case ChrW(47) ' /
                            sb.Append(ChrW(47))
                            _i += 1
                        Case ChrW(117) ' u
                            _i += 2
                            Dim hex = _s.Substring(_i, 4)
                            sb.Append(ChrW(Integer.Parse(hex, NumberStyles.HexNumber)))
                            _i += 4
                        Case Else
                            sb.Append(_s(_i))
                            _i += 1
                    End Select
                Else
                    sb.Append(c)
                    _i += 1
                End If
            End While
            Throw New FormatException("JSON 字符串未闭合")
        End Function

        Private Function ParseScalar() As JToken
            Dim start = _i
            While _i < _s.Length
                Select Case _s(_i)
                    Case ",", "}", "]", " ", vbTab, vbCr, vbLf
                        Exit While
                    Case Else
                        _i += 1
                End Select
            End While
            Dim s = _s.Substring(start, _i - start).Trim()
            SkipWs()
            If s = "null" OrElse s = "" OrElse s = "None" Then Return JValue.CreateNull()
            If s = "true" Then Return New JValue(True)
            If s = "false" Then Return New JValue(False)
            Dim lv As Long
            If Long.TryParse(s, NumberStyles.Any, CultureInfo.InvariantCulture, lv) Then Return New JValue(lv)
            Dim dv As Double
            If Double.TryParse(s, NumberStyles.Any, CultureInfo.InvariantCulture, dv) Then Return New JValue(dv)
            If s.Length > 0 Then Return New JValue(s)
            Throw New FormatException(ModLang.L("无法解析 JSON 值：") & s)
        End Function

    End Class

    ' ==================================================================
    '  写出
    ' ==================================================================
    Public Module JsonWriter

        Public Function Escape(s As String) As String
            If s Is Nothing Then Return ""
            Dim sb As New StringBuilder()
            For Each c In s
                Select Case c
                    Case ChrW(34) ' "
                        sb.Append("\")
                        sb.Append(ChrW(34))
                    Case ChrW(92) ' \
                        sb.Append("\")
                        sb.Append(ChrW(92))
                    Case vbCr
                        sb.Append("\")
                        sb.Append("r")
                    Case vbLf
                        sb.Append("\")
                        sb.Append("n")
                    Case vbTab
                        sb.Append("\")
                        sb.Append("t")
                    Case ChrW(8)
                        sb.Append("\")
                        sb.Append("b")
                    Case ChrW(12)
                        sb.Append("\")
                        sb.Append("f")
                    Case Else
                        If AscW(c) < 32 Then
                            sb.Append("\u")
                            sb.Append(AscW(c).ToString("x4"))
                        Else
                            sb.Append(c)
                        End If
                End Select
            Next
            Return sb.ToString()
        End Function

        Public Function EscapedString(s As String) As String
            Return ChrW(34) & Escape(s) & ChrW(34)
        End Function

    End Module

    Public Module JObjectWriter

        Public Function Write(o As JObject, formatting As Newtonsoft.Json.Formatting) As String
            If o Is Nothing Then Return "null"
            Dim props = o.GetPropertiesList()
            If props.Count = 0 Then Return "{}"
            Dim indent As String = If(formatting = Newtonsoft.Json.Formatting.Indented, " ", "")
            Dim sb As New StringBuilder()
            sb.Append("{")
            For idx As Integer = 0 To props.Count - 1
                Dim p As JProperty = props(idx)
                sb.Append(vbLf)
                sb.Append(indent)
                sb.Append(JsonWriter.EscapedString(p.Name))
                sb.Append(":")
                sb.Append(WriteValue(p.Value, formatting))
                If idx < props.Count - 1 Then sb.Append(",")
            Next
            sb.Append(vbLf)
            sb.Append(indent)
            sb.Append("}")
            Return sb.ToString()
        End Function

        Private Function WriteValue(v As JToken, formatting As Newtonsoft.Json.Formatting) As String
            If v Is Nothing Then Return "null"
            Select Case v.[Type]
                Case Newtonsoft.Json.JTokenType.[Object]
                    Return Write(CType(v, JObject), Newtonsoft.Json.Formatting.Indented)
                Case Newtonsoft.Json.JTokenType.[Array]
                    Return JArrayWriter.Write(CType(v, JArray), Newtonsoft.Json.Formatting.Indented)
                Case Newtonsoft.Json.JTokenType.[String]
                    Return JsonWriter.EscapedString(CStr(v.GetCoreValue()))
                Case Newtonsoft.Json.JTokenType.[Boolean]
                    Return If(CBool(v.GetCoreValue()), "true", "false")
                Case Newtonsoft.Json.JTokenType.[Date]
                    Return CDate(v.GetCoreValue()).ToString("o")
                Case Newtonsoft.Json.JTokenType.[Null]
                    Return "null"
                Case Else
                    Return TClJsonCast.ScalarText(v.GetCoreValue())
            End Select
        End Function

    End Module

    Public Module JArrayWriter

        Public Function Write(a As JArray, formatting As Newtonsoft.Json.Formatting) As String
            If a Is Nothing Then Return "null"
            Dim items = a.GetChildrenList()
            If items.Count = 0 Then Return "[]"
            Dim indent As String = If(formatting = Newtonsoft.Json.Formatting.Indented, " ", "")
            Dim sb As New StringBuilder()
            sb.Append("[")
            For idx As Integer = 0 To items.Count - 1
                Dim v As JToken = items(idx)
                sb.Append(vbLf)
                sb.Append(indent)
                If v Is Nothing Then
                    sb.Append("null")
                Else
                    Select Case v.[Type]
                        Case Newtonsoft.Json.JTokenType.[Object]
                            sb.Append(JObjectWriter.Write(CType(v, JObject), Newtonsoft.Json.Formatting.Indented))
                        Case Newtonsoft.Json.JTokenType.[Array]
                            sb.Append(Write(CType(v, JArray), Newtonsoft.Json.Formatting.Indented))
                        Case Newtonsoft.Json.JTokenType.[String]
                            sb.Append(JsonWriter.EscapedString(CStr(v.GetCoreValue())))
                        Case Newtonsoft.Json.JTokenType.[Boolean]
                            sb.Append(If(CBool(v.GetCoreValue()), "true", "false"))
                        Case Newtonsoft.Json.JTokenType.[Date]
                            sb.Append(CDate(v.GetCoreValue()).ToString("o"))
                        Case Newtonsoft.Json.JTokenType.[Null]
                            sb.Append("null")
                        Case Else
                            sb.Append(TClJsonCast.ScalarText(v.GetCoreValue()))
                    End Select
                End If
                If idx < items.Count - 1 Then sb.Append(",")
            Next
            sb.Append(vbLf)
            sb.Append(indent)
            sb.Append("]")
            Return sb.ToString()
        End Function

    End Module

    ' ==================================================================
    '  POCO 反射映射 + 值转换
    ' ==================================================================
    Public Module TClJsonCast

        Public Function DefaultOf(Of T)() As T
            Return Nothing
        End Function

        Public Function ScalarText(v As Object) As String
            If v Is Nothing Then Return ""
            If TypeOf v Is String Then Return CStr(v)
            If TypeOf v Is Integer Then Return CInt(v).ToString(CultureInfo.InvariantCulture)
            If TypeOf v Is Long Then Return CLng(v).ToString(CultureInfo.InvariantCulture)
            If TypeOf v Is Double Then Return CDbl(v).ToString("R", CultureInfo.InvariantCulture)
            If TypeOf v Is Single Then Return CSng(v).ToString("R", CultureInfo.InvariantCulture)
            If TypeOf v Is Decimal Then Return CDec(v).ToString(CultureInfo.InvariantCulture)
            If TypeOf v Is Boolean Then Return If(CBool(v), "true", "false")
            If TypeOf v Is Date Then Return CDate(v).ToString("o")
            If TypeOf v Is Byte() Then Return Convert.ToBase64String(CType(v, Byte()))
            Return CStr(v)
        End Function

        Public Function CastCore(Of T)(raw As Object) As T
            Return ValueToT(Of T)(raw, GetType(T))
        End Function

        Public Function ToT(Of T)(token As JToken) As T
            If token Is Nothing Then Return Nothing
            If token.[Type] = Newtonsoft.Json.JTokenType.[Object] Then
                Return POCOFromToken(Of T)(token, GetType(T))
            End If
            If token.[Type] = Newtonsoft.Json.JTokenType.[Array] Then
                Return ArrayToT(Of T)(CType(token, JArray), GetType(T))
            End If
            Return ValueToT(Of T)(token.GetCoreValue(), GetType(T))
        End Function

        Public Function ToT(Of T)(value As Object) As T
            Return ValueToT(Of T)(value, GetType(T))
        End Function

        Private Function ValueToT(Of T)(raw As Object, tType As Type) As T
            If raw Is Nothing Then Return Nothing
            If tType.IsInstanceOfType(raw) Then Return DirectCast(raw, T)
            If tType = GetType(String) Then
                Dim ss As Object = ScalarToText(raw)
                Return DirectCast(ss, T)
            End If
            If tType = GetType(Date) OrElse tType = GetType(DateTime) Then
                Dim d = ParseDateValue(raw)
                Return If(d Is Nothing, Nothing, DirectCast(d, T))
            End If
            If tType.IsEnum Then
                If TypeOf raw Is String Then
                    Dim s = CStr(raw)
                    If String.IsNullOrEmpty(s) Then Return Nothing
                    Return DirectCast([Enum].Parse(tType, s, True), T)
                End If
                Return DirectCast([Enum].ToObject(tType, ConvertNumberToInt(raw)), T)
            End If
            Dim d2 As Double
            If TryGetNumber(raw, d2) Then
                Dim typed = ToTypedNumber(tType, d2, raw)
                Try
                    Return DirectCast(typed, T)
                Catch
                End Try
            End If
            Try
                Return DirectCast(Convert.ChangeType(raw, tType, CultureInfo.InvariantCulture), T)
            Catch
                Return Nothing
            End Try
        End Function

        Private Function ScalarToText(raw As Object) As String
            If raw Is Nothing Then Return ""
            If TypeOf raw Is String Then Return CStr(raw)
            Return ScalarText(raw)
        End Function

        Private Function TryGetNumber(raw As Object, ByRef outVal As Double) As Boolean
            If raw Is Nothing Then Return False
            If TypeOf raw Is String Then
                Return Double.TryParse(CStr(raw), NumberStyles.Any, CultureInfo.InvariantCulture, outVal)
            End If
            Try
                outVal = CDbl(Convert.ChangeType(raw, GetType(Double), CultureInfo.InvariantCulture))
                Return True
            Catch
                Return False
            End Try
        End Function

        Private Function ToTypedNumber(tType As Type, d As Double, raw As Object) As Object
            If TypeOf raw Is Integer OrElse TypeOf raw Is Long OrElse TypeOf raw Is Short OrElse TypeOf raw Is Byte OrElse TypeOf raw Is SByte OrElse TypeOf raw Is UInteger OrElse TypeOf raw Is ULong Then
                Try
                    Return Convert.ChangeType(raw, tType, CultureInfo.InvariantCulture)
                Catch
                End Try
            End If
            Try
                Return Convert.ChangeType(d, tType, CultureInfo.InvariantCulture)
            Catch
                Return d
            End Try
        End Function

        Private Function ConvertNumberToInt(raw As Object) As Integer
            Try
                Return CInt(Convert.ChangeType(raw, GetType(Integer), CultureInfo.InvariantCulture))
            Catch
                Return 0
            End Try
        End Function

        Private Function ParseDateValue(raw As Object) As Object
            If raw Is Nothing Then Return Nothing
            If TypeOf raw Is Date Then Return raw
            Dim s = CStr(raw).Trim()
            If String.IsNullOrEmpty(s) Then Return Nothing
            Dim d As DateTime
            Dim fmts As String() = {"o", "yyyy-MM-ddTHH:mm:ss", "yyyy-MM-ddTHH:mm:ssZ", "yyyy-MM-ddTHH:mm:ss.fffffffZ", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"}
            Dim styles As System.Globalization.DateTimeStyles = System.Globalization.DateTimeStyles.AssumeLocal
            For Each f In fmts
                If DateTime.TryParseExact(s, f, CultureInfo.InvariantCulture, styles, d) Then Return d
            Next
            If DateTime.TryParse(s, CultureInfo.InvariantCulture, styles, d) Then Return d
            Return Nothing
        End Function

        Private Function ArrayToT(Of T)(arr As JArray, tType As Type) As T
            Dim items = arr.GetChildrenList()
            If tType.IsGenericType Then
                Dim genDef As Type = tType.GetGenericTypeDefinition()
                If genDef = GetType(List(Of Object)) OrElse genDef = GetType(IList(Of Object)) OrElse genDef = GetType(ICollection(Of Object)) OrElse genDef = GetType(IEnumerable(Of Object)) Then
                    Dim elem = tType.GetGenericArguments()(0)
                    Dim li As New List(Of Object)()
                    For Each it In items
                        li.Add(ElementForType(it, elem))
                    Next
                    Dim oo As Object = li : Return DirectCast(oo, T)
                End If
            End If
            If tType.IsArray Then
                Dim elem = tType.GetElementType()
                Dim ao = Activator.CreateInstance(tType, items.Count)
                For i As Integer = 0 To items.Count - 1
                    ao.SetValue(ElementForType(items(i), elem), i)
                Next
                Return DirectCast(ao, T)
            End If
            If items.Count = 0 Then Return Nothing
            Return ElementForType(items(0), tType)
        End Function

        Private Function ElementForType(token As JToken, target As Type) As Object
            If token Is Nothing Then Return Nothing
            If target = GetType(JToken) OrElse target = GetType(JObject) OrElse target = GetType(JArray) Then
                Return token
            End If
            If target = GetType(String) Then
                Dim v = token.GetCoreValue()
                Return If(v Is Nothing, Nothing, ScalarToText(v))
            End If
            If token.[Type] = Newtonsoft.Json.JTokenType.[Object] OrElse token.[Type] = Newtonsoft.Json.JTokenType.[Array] Then
                Return POCOFromToken(Of Object)(token, target)
            End If
            Return ValueToT(Of Object)(token.GetCoreValue(), target)
        End Function

        Private Function POCOFromToken(Of T)(token As JToken, target As Type) As T
            If target = GetType(JToken) OrElse target = GetType(JObject) OrElse target = GetType(JArray) Then
                Dim oo As Object = token : Return DirectCast(oo, T)
            End If
            If token.[Type] = Newtonsoft.Json.JTokenType.[Array] Then
                Dim arr As JArray = CType(token, JArray)
                If target.IsGenericType Then
                    Dim genDef As Type = target.GetGenericTypeDefinition()
                    If genDef = GetType(List(Of Object)) OrElse genDef = GetType(IList(Of Object)) OrElse genDef = GetType(ICollection(Of Object)) OrElse genDef = GetType(IEnumerable(Of Object)) Then
                        Dim elem = target.GetGenericArguments()(0)
                        Dim li As New List(Of Object)()
                        For Each it In arr.GetChildrenList()
                            li.Add(ElementForType(it, elem))
                        Next
                        Dim oo As Object = li : Return DirectCast(oo, T)
                    End If
                End If
                If target.IsArray Then
                    Dim elem = target.GetElementType()
                    Dim ao = Activator.CreateInstance(target, arr.GetChildrenList().Count)
                    For i As Integer = 0 To arr.GetChildrenList().Count - 1
                        ao.SetValue(ElementForType(arr.GetChildrenList()(i), elem), i)
                    Next
                    Return DirectCast(ao, T)
                End If
            End If
            If Not target.IsClass OrElse target = GetType(String) OrElse target = GetType(Date) OrElse target = GetType(DateTime) OrElse target.IsEnum Then
                Return ValueToT(Of T)(token.GetCoreValue(), target)
            End If
            Dim jo As JObject
            If token.[Type] = Newtonsoft.Json.JTokenType.[Object] Then
                jo = CType(token, JObject)
            Else
                jo = New JObject()
            End If
            Dim obj = Activator.CreateInstance(target)
            For Each p In jo.GetPropertiesList()
                Dim mi = FindMember(target, p.Name)
                If mi Is Nothing Then Continue For
                Dim val = ElementForType(p.Value, mi.MemberType)
                SetMember(obj, mi, val)
            Next
            Return DirectCast(obj, T)
        End Function

        Public Function FromObject(obj As Object) As JToken
            If obj Is Nothing Then Return JValue.CreateNull()
            If TypeOf obj Is JToken Then Return CType(obj, JToken)
            If TypeOf obj Is String Then Return New JValue(CStr(obj))
            If TypeOf obj Is Boolean Then Return New JValue(CBool(obj))
            If TypeOf obj Is Date OrElse TypeOf obj Is DateTime Then Return New JValue(CDate(obj))
            If IsNumericValue(obj) Then Return New JValue(obj)
            If TypeOf obj Is Byte() Then Return New JValue(CType(obj, Byte()))
            If TypeOf obj Is System.Collections.IEnumerable Then
                Dim arr As New JArray()
                For Each it In CType(obj, System.Collections.IEnumerable)
                    arr.Add(FromObject(it))
                Next
                Return arr
            End If
            Dim jo As New JObject()
            FillObject(jo, obj)
            Return jo
        End Function

        Private Function IsNumericValue(o As Object) As Boolean
            Return TypeOf o Is Integer OrElse TypeOf o Is Long OrElse TypeOf o Is Short OrElse TypeOf o Is Byte OrElse TypeOf o Is SByte OrElse TypeOf o Is UInteger OrElse TypeOf o Is ULong OrElse TypeOf o Is Double OrElse TypeOf o Is Single OrElse TypeOf o Is Decimal
        End Function

        Public Sub FillObject(target As JObject, source As Object)
            If source Is Nothing Then Return
            Dim type = source.GetType()
            For Each fld In type.GetFields(Reflection.BindingFlags.Public Or Reflection.BindingFlags.Instance)
                target.Item(fld.Name) = FromObject(fld.GetValue(source))
            Next
            For Each prp In type.GetProperties(Reflection.BindingFlags.Public Or Reflection.BindingFlags.Instance)
                If prp.GetIndexParameters().Length > 0 Then Continue For
                Try
                    target.Item(prp.Name) = FromObject(prp.GetValue(source, Nothing))
                Catch
                End Try
            Next
        End Sub

        Public Sub FillArray(target As JArray, source As Object)
            If source Is Nothing Then Return
            For Each it In CType(source, System.Collections.IEnumerable)
                target.Add(FromObject(it))
            Next
        End Sub

        Private Class MemberInfoEx
            Public MemberType As Type
            Public IsField As Boolean
            Public Name As String
            Public IsEnum As Boolean
        End Class
        Private _memberCache As New Dictionary(Of String, MemberInfoEx)()
        Private _memberLock As New Object()

        Private Function FindMember(target As Type, name As String) As MemberInfoEx
            Dim key = target.FullName + "|" + name.ToLowerInvariant()
            SyncLock _memberLock
                If _memberCache.ContainsKey(key) Then Return _memberCache(key)
            End SyncLock
            Dim mi = ResolveMember(target, name, False)
            If mi Is Nothing Then mi = ResolveMember(target, name, True)
            SyncLock _memberLock
                _memberCache(key) = mi
            End SyncLock
            Return mi
        End Function

        Private Function ResolveMember(target As Type, name As String, ignoreCase As Boolean) As MemberInfoEx
            Dim cmp = If(ignoreCase, StringComparison.OrdinalIgnoreCase, StringComparison.Ordinal)
            For Each fld In target.GetFields(BindingFlags.Public Or BindingFlags.Instance)
                If String.Equals(fld.Name, name, cmp) Then
                    Return New MemberInfoEx With {.MemberType = fld.FieldType, .IsField = True, .Name = fld.Name, .IsEnum = fld.FieldType.IsEnum}
                End If
            Next
            For Each prp In target.GetProperties(BindingFlags.Public Or BindingFlags.Instance)
                If String.Equals(prp.Name, name, cmp) AndAlso prp.CanWrite Then
                    Return New MemberInfoEx With {.MemberType = prp.PropertyType, .IsField = False, .Name = prp.Name, .IsEnum = prp.PropertyType.IsEnum}
                End If
            Next
            Return Nothing
        End Function

        Private _fieldCache As New Dictionary(Of String, Reflection.FieldInfo)()
        Private _propCache As New Dictionary(Of String, Reflection.PropertyInfo)()
        Private Function GetField(obj As Object, name As String) As Reflection.FieldInfo
            Dim key = obj.GetType().FullName + "|" + name
            If Not _fieldCache.ContainsKey(key) Then
                _fieldCache(key) = obj.GetType().GetField(name, BindingFlags.Public Or BindingFlags.Instance)
            End If
            Return _fieldCache(key)
        End Function
        Private Function GetProp(obj As Object, name As String) As Reflection.PropertyInfo
            Dim key = obj.GetType().FullName + "|" + name
            If Not _propCache.ContainsKey(key) Then
                _propCache(key) = obj.GetType().GetProperty(name, BindingFlags.Public Or BindingFlags.Instance)
            End If
            Return _propCache(key)
        End Function

        Private Sub SetMember(obj As Object, mi As MemberInfoEx, val As Object)
            If mi Is Nothing Then Return
            If val IsNot Nothing Then
                If mi.IsEnum Then
                    If TypeOf val Is String Then
                        Dim s = CStr(val)
                        If Not String.IsNullOrEmpty(s) Then val = [Enum].Parse(mi.MemberType, s, True)
                    ElseIf TypeOf val Is System.IConvertible Then
                        Try
                            val = [Enum].ToObject(mi.MemberType, CInt(Convert.ChangeType(val, GetType(Integer), CultureInfo.InvariantCulture)))
                        Catch
                        End Try
                    End If
                End If
                If Not mi.MemberType.IsInstanceOfType(val) Then
                    Try
                        If mi.MemberType = GetType(String) Then
                            val = If(val Is Nothing, Nothing, ScalarToText(val))
                        Else
                            val = Convert.ChangeType(val, mi.MemberType, CultureInfo.InvariantCulture)
                        End If
                    Catch
                    End Try
                End If
            End If
            If mi.IsField Then
                GetField(obj, mi.Name).SetValue(obj, val)
            Else
                GetProp(obj, mi.Name).SetValue(obj, val, Nothing)
            End If
        End Sub

        Public Function ToObjectCore(token As JToken) As Object
            If token Is Nothing Then Return Nothing
            If token.[Type] = Newtonsoft.Json.JTokenType.[Object] Then Return token.GetCoreValue()
            If token.[Type] = Newtonsoft.Json.JTokenType.[Array] Then
                Dim arr As JArray = CType(token, JArray)
                Dim l As New List(Of Object)()
                For Each c In arr.GetChildrenList()
                    l.Add(If(c.GetCoreValue() Is Nothing, Nothing, ToObjectCore(c)))
                Next
                Return l
            End If
            Return token.GetCoreValue()
        End Function

    End Module

End Namespace
