using System.Windows.Media;

namespace TClUI.Controls.SvgIcon;

internal readonly record struct SvgIconPaintOptions(
    Brush IconBrush,
    double StrokeThickness,
    bool UseOriginalColor);