using System.Globalization;
using System.Text.RegularExpressions;

namespace TClUI.Controls.SvgIcon;

internal static class SvgNumberParser
{
    private static readonly Regex _NumberRegexValue = new(@"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", RegexOptions.Compiled);

    public static double TryParse(string? value, double fallback = 0D)
    {
        var parsed = TryParseNullable(value);
        return parsed ?? fallback;
    }

    public static double? TryParseNullable(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return null;

        var match = _NumberRegexValue.Match(value!.Trim());
        if (!match.Success)
            return null;

        return double.TryParse(match.Value, NumberStyles.Float, CultureInfo.InvariantCulture, out var result)
            ? result
            : null;
    }

    public static double[] ParseNumberList(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return [];

        var matches = _NumberRegexValue.Matches(value);
        var result = new double[matches.Count];
        for (var i = 0; i < matches.Count; i++)
            result[i] = double.Parse(matches[i].Value, NumberStyles.Float, CultureInfo.InvariantCulture);

        return result;
    }
}