' ============================================================================
'  TClKit.Value —— 值类型工具（数学、范围、OneOf 判别）
'  由 TCl 团队原创实现（保持 TClKit.Value 命名空间与成员名，主工程调用点不变）。
'  零第三方依赖。
' ============================================================================
Option Explicit On

Imports System.Numerics
Imports System.Collections.Generic
Imports System.Runtime.CompilerServices

Namespace TClKit.Value

    ''' <summary>数学工具（进制、插值、质数、最大公约数）。</summary>
    Public Module MathUtils

        Private Const Digits As String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/+=!?@#$%^&*()[]{}<>;:',"

        ''' <summary>把字符串按指定进制数字符转成目标进制字符串（2~86）。</summary>
        <Extension>
        Public Function ConvertRadix(input As String, fromRadix As Integer, toRadix As Integer) As String
            If fromRadix < 2 OrElse fromRadix > Digits.Length Then
                Throw New ArgumentOutOfRangeException("fromRadix")
            End If
            If String.IsNullOrEmpty(input) Then Return "0"
            Dim isNegative = input.StartsWith("-")
            If isNegative Then input = input.TrimStart("-"c)
            Dim realNum As BigInteger = 0
            For Each c In input
                Dim digit = Digits.IndexOf(c)
                If digit = -1 OrElse digit >= fromRadix Then
                    Throw New ArgumentException($"Character '{c}' not a valid digit for radix {fromRadix}.")
                End If
                realNum = realNum * fromRadix + digit
            Next
            Return (If(isNegative, realNum * -1, realNum)).ConvertRadix(toRadix)
        End Function

        <Extension>
        Public Function ConvertRadix(input As Long, toRadix As Integer) As String
            Return BigInteger.New(input).ConvertRadix(toRadix)
        End Function
        <Extension>
        Public Function ConvertRadix(input As ULong, toRadix As Integer) As String
            Return BigInteger.New(input).ConvertRadix(toRadix)
        End Function
        <Extension>
        Public Function ConvertRadix(input As Integer, toRadix As Integer) As String
            Return BigInteger.New(input).ConvertRadix(toRadix)
        End Function
        <Extension>
        Public Function ConvertRadix(input As Byte, toRadix As Integer) As String
            Return BigInteger.New(input).ConvertRadix(toRadix)
        End Function
        <Extension>
        Public Function ConvertRadix(input As BigInteger, toRadix As Integer) As String
            If toRadix < 2 OrElse toRadix > Digits.Length Then
                Throw New ArgumentOutOfRangeException("toRadix")
            End If
            If input = 0 Then Return "0"
            Dim isNegative = input < 0
            Dim results As New List(Of Char)()
            Dim remaining = BigInteger.Abs(input)
            Dim bigRadix As BigInteger = toRadix
            While remaining > 0
                Dim rem As BigInteger
                remaining = BigInteger.DivRem(remaining, bigRadix, rem)
                results.Add(Digits(CInt(rem)))
            End While
            If isNegative Then results.Add("-"c)
            results.Reverse()
            Return New String(results.ToArray())
        End Function

        ''' <summary>把值限制在 [min, max] 范围内。</summary>
        <Extension>
        Public Function Clamp(Of T As {Inherits IComparable(Of T)})(value As T, min As T, max As T) As T
            If value.CompareTo(min) < 0 Then Return min
            If value.CompareTo(max) > 0 Then Return max
            Return value
        End Function

        ''' <summary>线性插值，结果舍入到 7 位小数。</summary>
        Public Function Lerp(start As Double, end As Double, percentage As Double) As Double
            Return Math.Round(start + (end - start) * percentage, 7)
        End Function

        ''' <summary>计算二阶贝塞尔曲线。</summary>
        Public Function Bezier(t As Double, x1 As Double, y1 As Double, x2 As Double, y2 As Double, Optional accuracy As Double = 0.01) As Double
            If t < 0.0000001 OrElse Double.IsNaN(t) Then Return 0
            If t > 0.9999999 Then Return 1
            Dim a As Double = t
            Dim b As Double
            Dim maxIterations As Integer = 1000
            Do
                b = 3 * a * ((0.33333333 + x1 - x2) * a * a + (x2 - 2 * x1) * a + x1)
                a += (t - b) * 0.5
                maxIterations -= 1
            Loop While (Not (Math.Abs(b - t) < accuracy)) AndAlso maxIterations > 0
            Return 3 * a * ((0.33333333 + y1 - y2) * a * a + (y2 - 2 * y1) * a + y1)
        End Function

        ''' <summary>判断是否近似等于目标值（默认容差 1e-6）。</summary>
        <Extension>
        Public Function IsNear(value As Double, target As Double, Optional tolerance As Double = 1E-6) As Boolean
            Return Math.Abs(value - target) < tolerance
        End Function

        ''' <summary>质因数分解。</summary>
        Public Function GetPrimeFactors(num As Integer) As IEnumerable(Of Integer)
            num = Math.Abs(num)
            While num Mod 2 = 0 AndAlso num > 1
                Yield 2
                num \/= 2
            End While
            Dim i As Integer = 3
            Dim maxFactor = CInt(Math.Sqrt(num))
            While i <= maxFactor
                While num Mod i = 0
                    Yield i
                    num \/= i
                    maxFactor = CInt(Math.Sqrt(num))
                End While
                i += 2
            End While
            If num > 2 Then Yield num
        End Function

        ''' <summary>最大公约数。</summary>
        Public Function Gcd(a As Integer, b As Integer) As Integer
            a = Math.Abs(a)
            b = Math.Abs(b)
            While b <> 0
                Dim temp = b
                b = a Mod b
                a = temp
            End While
            Return a
        End Function
        Public Function Gcd(a As Double, b As Integer) As Integer
            Return Gcd(CInt(Math.Round(a)), b)
        End Function

    End Module

    ''' <summary>带可选下界/上界的范围。</summary>
    Public Class ValueRange(Of T As {Inherits IComparable(Of T)})
        Implements System.IEquatable(Of ValueRange(Of T))

        Public Property [Lower] As T
        Public Property [Upper] As T
        Public Property HasLower As Boolean
        Public Property HasUpper As Boolean
        Public Property IsLowerInclusive As Boolean
        Public Property IsUpperInclusive As Boolean

        Public Sub New(Optional lower As T = Nothing, Optional upper As T = Nothing,
                       Optional isLowerInclusive As Boolean = True, Optional isUpperInclusive As Boolean = True,
                       Optional hasLower As Boolean = False, Optional hasUpper As Boolean = False)
            If hasLower AndAlso IsNothing(lower) Then Throw New ArgumentNullException("lower")
            If hasUpper AndAlso IsNothing(upper) Then Throw New ArgumentNullException("upper")
            Me.Lower = lower
            Me.Upper = upper
            Me.HasLower = hasLower
            Me.HasUpper = hasUpper
            Me.IsLowerInclusive = isLowerInclusive
            Me.IsUpperInclusive = isUpperInclusive
        End Sub

        Public Sub ClearLower()
            Me.Lower = Nothing
            Me.HasLower = False
        End Sub
        Public Sub ClearUpper()
            Me.Upper = Nothing
            Me.HasUpper = False
        End Sub
        Public Sub SetLower(lower As T, isInclusive As Boolean)
            Me.Lower = lower
            Me.HasLower = True
            Me.IsLowerInclusive = isInclusive
        End Sub
        Public Sub SetUpper(upper As T, isInclusive As Boolean)
            Me.Upper = upper
            Me.HasUpper = True
            Me.IsUpperInclusive = isInclusive
        End Sub

        ' 工厂
        Public Shared Function Closed(lower As T, upper As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(lower, upper, True, True, True, True)
        End Function
        Public Shared Function ClosedOpen(lower As T, upper As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(lower, upper, True, False, True, True)
        End Function
        Public Shared Function OpenClosed(lower As T, upper As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(lower, upper, False, True, True, True)
        End Function
        Public Shared Function Open(lower As T, upper As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(lower, upper, False, False, True, True)
        End Function
        Public Shared Function Exactly(value As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(value, value, True, True, True, True)
        End Function
        Public Shared Function AtLeast(lower As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(lower, Nothing, True, True, True, False)
        End Function
        Public Shared Function GreaterThan(lower As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(lower, Nothing, False, True, True, False)
        End Function
        Public Shared Function AtMost(upper As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(Nothing, upper, True, True, False, True)
        End Function
        Public Shared Function LessThan(upper As T) As ValueRange(Of T)
            Return New ValueRange(Of T)(Nothing, upper, True, False, False, True)
        End Function
        Public Shared Function [All]() As ValueRange(Of T)
            Return New ValueRange(Of T)()
        End Function

        ''' <summary>从字符串解析范围，如 "[1.0, 2.0)"。</summary>
        Public Shared Function FromString(str As String, Optional parseFunc As Func(Of String, T) = Nothing) As ValueRange(Of T)
            If String.IsNullOrEmpty(str) Then Throw New ArgumentException("不能为空")
            Dim isLowerInclusive = str(0) = "["c
            Dim isUpperInclusive = str(str.Length - 1) = "]"c
            If Not isLowerInclusive AndAlso str(0) <> "("c Then Throw New ArgumentException("开头必须是 [ 或 (")
            If Not isUpperInclusive AndAlso str(str.Length - 1) <> ")"c Then Throw New ArgumentException("结尾必须是 ] 或 )")
            Dim commaIndex = -1
            For idx As Integer = 0 To str.Length - 1
                If str(idx) = ","c OrElse str(idx) = "，"c Then
                    commaIndex = idx
                    Exit For
                End If
            Next
            If commaIndex = -1 Then Throw New ArgumentException("需要用逗号分隔两个值")
            Dim lowerStr = str.Substring(1, commaIndex - 1).Trim()
            Dim upperStr = str.Substring(commaIndex + 1, str.Length - commaIndex - 2).Trim()
            Dim lo As T = Nothing, hi As T = Nothing
            Dim hasLower As Boolean = False, hasUpper As Boolean = False
            If Not String.IsNullOrEmpty(lowerStr) AndAlso lowerStr <> "-∞" Then
                lo = If(parseFunc IsNot Nothing, parseFunc(lowerStr), CType(Convert.ChangeType(lowerStr, GetType(T)), T))
                hasLower = True
            End If
            If Not String.IsNullOrEmpty(upperStr) AndAlso upperStr <> "+∞" AndAlso upperStr <> "∞" Then
                hi = If(parseFunc IsNot Nothing, parseFunc(upperStr), CType(Convert.ChangeType(upperStr, GetType(T)), T))
                hasUpper = True
            End If
            Return New ValueRange(Of T)(lo, hi, isLowerInclusive, isUpperInclusive, hasLower, hasUpper)
        End Function

        Public Overrides Function ToString() As String
            Dim left = If(HasLower AndAlso IsLowerInclusive, "[", "(")
            Dim lowerTxt = If(HasLower, [Lower].ToString(), "-∞")
            Dim upperTxt = If(HasUpper, [Upper].ToString(), "+∞")
            Dim right = If(HasUpper AndAlso IsUpperInclusive, "]", ")")
            Return $"{left}{lowerTxt}, {upperTxt}{right}"
        End Function

        ''' <summary>判断值是否在范围内。</summary>
        Public Function Contains(value As T) As Boolean
            If HasLower Then
                Dim cmp = value.CompareTo([Lower])
                If cmp < 0 Then Return False
                If cmp = 0 AndAlso Not IsLowerInclusive Then Return False
            End If
            If HasUpper Then
                Dim cmp = value.CompareTo([Upper])
                If cmp > 0 Then Return False
                If cmp = 0 AndAlso Not IsUpperInclusive Then Return False
            End If
            Return True
        End Function

        ''' <summary>该范围是否不包含任何值。</summary>
        Public Function IsEmpty() As Boolean
            If Not HasLower OrElse Not HasUpper Then Return False
            Dim compared = [Lower].CompareTo([Upper])
            Return compared > 0 OrElse (compared = 0 AndAlso (Not IsLowerInclusive OrElse Not IsUpperInclusive))
        End Function

        ''' <summary>取与另一范围的交集；若为空返回 Nothing。</summary>
        Public Function Intersect(other As ValueRange(Of T)) As ValueRange(Of T)
            Dim lo As T = Nothing, hi As T = Nothing
            Dim isLowerInclusive As Boolean = True, isUpperInclusive As Boolean = True
            If HasLower AndAlso other.HasLower Then
                Select Case [Lower].CompareTo(other.[Lower])
                    Case Is > 0
                        lo = [Lower] : isLowerInclusive = IsLowerInclusive
                    Case Is < 0
                        lo = other.[Lower] : isLowerInclusive = other.IsLowerInclusive
                    Case Else
                        lo = [Lower] : isLowerInclusive = IsLowerInclusive AndAlso other.IsLowerInclusive
                End Select
            ElseIf HasLower Then
                lo = [Lower] : isLowerInclusive = IsLowerInclusive
            ElseIf other.HasLower Then
                lo = other.[Lower] : isLowerInclusive = other.IsLowerInclusive
            End If
            If HasUpper AndAlso other.HasUpper Then
                Select Case [Upper].CompareTo(other.[Upper])
                    Case Is < 0
                        hi = [Upper] : isUpperInclusive = IsUpperInclusive
                    Case Is > 0
                        hi = other.[Upper] : isUpperInclusive = other.IsUpperInclusive
                    Case Else
                        hi = [Upper] : isUpperInclusive = IsUpperInclusive AndAlso other.IsUpperInclusive
                End Select
            ElseIf HasUpper Then
                hi = [Upper] : isUpperInclusive = IsUpperInclusive
            ElseIf other.HasUpper Then
                hi = other.[Upper] : isUpperInclusive = other.IsUpperInclusive
            End If
            Dim anyHasLower = HasLower Or other.HasLower
            Dim anyHasUpper = HasUpper Or other.HasUpper
            Return New ValueRange(Of T)(lo, hi, isLowerInclusive, isUpperInclusive, anyHasLower, anyHasUpper)
        End Function

        ' 相等判断
        Public Overrides Function Equals(obj As Object) As Boolean
            Return Equals(If(TypeOf obj Is ValueRange(Of T), DirectCast(obj, ValueRange(Of T)), Nothing))
        End Function
        Public Function Equals(other As ValueRange(Of T)) As Boolean
            If other Is Nothing Then Return False
            Return EqualityComparer(Of T).Default.Equals([Lower], other.[Lower]) AndAlso
                   EqualityComparer(Of T).Default.Equals([Upper], other.[Upper]) AndAlso
                   HasLower = other.HasLower AndAlso HasUpper = other.HasUpper AndAlso
                   IsLowerInclusive = other.IsLowerInclusive AndAlso IsUpperInclusive = other.IsUpperInclusive
        End Function
        Public Overrides Function GetHashCode() As Integer
            Dim hashCode As Integer = 169504101
            hashCode = unchecked(hashCode * -1521134295) + EqualityComparer(Of T).Default.GetHashCode([Lower])
            hashCode = unchecked(hashCode * -1521134295) + EqualityComparer(Of T).Default.GetHashCode([Upper])
            hashCode = unchecked(hashCode * -1521134295) + HasLower.GetHashCode()
            hashCode = unchecked(hashCode * -1521134295) + HasUpper.GetHashCode()
            hashCode = unchecked(hashCode * -1521134295) + IsLowerInclusive.GetHashCode()
            hashCode = unchecked(hashCode * -1521134295) + IsUpperInclusive.GetHashCode()
            Return hashCode
        End Function
        Public Shared Operator =(left As ValueRange(Of T), right As ValueRange(Of T)) As Boolean
            Return EqualityComparer(Of ValueRange(Of T)).Default.Equals(left, right)
        End Operator
        Public Shared Operator <>(left As ValueRange(Of T), right As ValueRange(Of T)) As Boolean
            Return Not (left = right)
        End Operator
    End Class

    ''' <summary>两种类型判别（OneOf）。</summary>
    Public Structure OneOf(Of T1, T2)
        Private index As Integer ' -1 未初始化
        Private value1 As T1
        Private value2 As T2

        Public Sub New()
            Me.index = -1
        End Sub
        Public Sub New(index As Integer, value1 As T1, value2 As T2)
            Me.index = index
            Me.value1 = value1
            Me.value2 = value2
        End Sub

        ' 隐式：T1/T2 -> OneOf
        Public Shared Widening Operator CType(value As T1) As OneOf(Of T1, T2)
            Dim o As New OneOf(Of T1, T2)(1, value, Nothing)
            Return o
        End Operator
        Public Shared Widening Operator CType(value As T2) As OneOf(Of T1, T2)
            Dim o As New OneOf(Of T1, T2)(2, Nothing, value)
            Return o
        End Operator
        ' 隐式：OneOf -> T1/T2
        Public Shared Widening Operator CType(value As OneOf(Of T1, T2)) As T1
            Return value.AsT1()
        End Operator
        Public Shared Widening Operator CType(value As OneOf(Of T1, T2)) As T2
            Return value.AsT2()
        End Operator

        Public Function Switch(Of TResult)(f1 As Func(Of T1, TResult), f2 As Func(Of T2, TResult)) As TResult
            Select Case index
                Case 1 : Return f1(value1)
                Case 2 : Return f2(value2)
                Case Else : Throw New InvalidOperationException()
            End Select
        End Function
        Public Sub Switch(f1 As Action(Of T1), f2 As Action(Of T2))
            If index = 1 Then f1(value1)
            ElseIf index = 2 Then f2(value2)
            Else Throw New InvalidOperationException()
            End If
        End Sub
        Public Function Is(Of V)() As Boolean
            Select Case index
                Case 1 : If GetType(V) = GetType(T1) Then Return True
                Case 2 : If GetType(V) = GetType(T2) Then Return True
            End Select
            Return False
        End Function
        Public Function IsT1() As Boolean
            Return index = 1
        End Function
        Public Function IsT2() As Boolean
            Return index = 2
        End Function
        Public Function As(Of V)() As V
            Select Case index
                Case 1
                    If GetType(V) = GetType(T1) Then
                        Dim o As Object = value1
                        Return DirectCast(o, V)
                    End If
                Case 2
                    If GetType(V) = GetType(T2) Then
                        Dim o As Object = value2
                        Return DirectCast(o, V)
                    End If
            End Select
            Throw New InvalidOperationException()
        End Function
        Public Function AsT1() As T1
            If index = 1 Then Return value1
            Throw New InvalidOperationException()
        End Function
        Public Function AsT2() As T2
            If index = 2 Then Return value2
            Throw New InvalidOperationException()
        End Function
    End Structure

    ''' <summary>三种类型判别（OneOf）。</summary>
    Public Structure OneOf(Of T1, T2, T3)
        Private index As Integer
        Private value1 As T1
        Private value2 As T2
        Private value3 As T3

        Public Sub New()
            Me.index = -1
        End Sub

        Public Shared Widening Operator CType(value As T1) As OneOf(Of T1, T2, T3)
            Return New OneOf(Of T1, T2, T3)(1, value, Nothing, Nothing)
        End Operator
        Public Shared Widening Operator CType(value As T2) As OneOf(Of T1, T2, T3)
            Return New OneOf(Of T1, T2, T3)(2, Nothing, value, Nothing)
        End Operator
        Public Shared Widening Operator CType(value As T3) As OneOf(Of T1, T2, T3)
            Return New OneOf(Of T1, T2, T3)(3, Nothing, Nothing, value)
        End Operator

        Public Function As(Of V)() As V
            Select Case index
                Case 1
                    If GetType(V) = GetType(T1) Then
                        Dim o As Object = value1
                        Return DirectCast(o, V)
                    End If
                Case 2
                    If GetType(V) = GetType(T2) Then
                        Dim o As Object = value2
                        Return DirectCast(o, V)
                    End If
                Case 3
                    If GetType(V) = GetType(T3) Then
                        Dim o As Object = value3
                        Return DirectCast(o, V)
                    End If
            End Select
            Throw New InvalidOperationException()
        End Function
        Public Function AsT1() As T1
            If index = 1 Then Return value1
            Throw New InvalidOperationException()
        End Function
        Public Function AsT2() As T2
            If index = 2 Then Return value2
            Throw New InvalidOperationException()
        End Function
        Public Function AsT3() As T3
            If index = 3 Then Return value3
            Throw New InvalidOperationException()
        End Function
        Public Function IsT1() As Boolean
            Return index = 1
        End Function
        Public Function IsT2() As Boolean
            Return index = 2
        End Function
        Public Function IsT3() As Boolean
            Return index = 3
        End Function
    End Structure

End Namespace
