using System;
using System.Collections.Concurrent;
using System.IO;
using System.Linq;
using System.Text;
using System.Windows;

namespace TClUI.Controls.SvgIcon;

public static class SvgIconLoader
{
    public const string DefaultIconPack = "default";

    private static readonly string _AssemblyName =
        typeof(SvgIconLoader).Assembly.GetName().Name ?? "TClUI";

    private static readonly ConcurrentDictionary<string, Lazy<SvgIconModel?>> _Cache =
        new(StringComparer.OrdinalIgnoreCase);

    internal static SvgIconModel? Load(string? icon, string? defaultPack = null)
    {
        var key = SvgIconKey.TryParse(icon, defaultPack ?? DefaultIconPack);
        if (key is null)
        {
            if (!string.IsNullOrWhiteSpace(icon))
            return null;
        }

        var cacheKey = key!.Value.ToString();
        return _Cache.GetOrAdd(cacheKey, _ => new Lazy<SvgIconModel?>(() => _LoadCore(key.Value))).Value;
    }

    public static void ClearCache()
    {
        _Cache.Clear();
    }

    private static SvgIconModel? _LoadCore(SvgIconKey key)
    {
        var suffix = $"IconPacks/{key.Pack}/{key.Name}.svg";
        try
        {
            var assembly = typeof(SvgIconLoader).Assembly;
            var resourceName = assembly.GetManifestResourceNames()
                .FirstOrDefault(n => n.Replace('\\', '/').EndsWith(suffix, StringComparison.OrdinalIgnoreCase));
            if (resourceName is not null)
            {
                using var resourceStream = assembly.GetManifestResourceStream(resourceName);
                if (resourceStream is not null)
                {
                    using var resourceReader = new StreamReader(resourceStream, Encoding.UTF8, true);
                    return SvgIconParser.Parse(resourceReader.ReadToEnd());
                }
            }

            var uri = new Uri(
                $"pack://application:,,,/{_AssemblyName};component/UI/Assets/{suffix}",
                UriKind.Absolute);
            var info = Application.GetResourceStream(uri);
            if (info is null)
            {
                return null;
            }

            using var stream = info.Stream;
            using var reader = new StreamReader(stream, Encoding.UTF8, true);
            var svg = reader.ReadToEnd();
            return SvgIconParser.Parse(svg);
        }
        catch (Exception)
        {
            return null;
        }
    }


    private readonly record struct SvgIconKey(string Pack, string Name)
    {
        public static SvgIconKey? TryParse(string? icon, string defaultPack)
        {
            if (string.IsNullOrWhiteSpace(icon))
                return null;

            var normalized = icon!.Trim().Replace('\\', '/');
            if (normalized.EndsWith(".svg", StringComparison.OrdinalIgnoreCase))
                normalized = normalized[..^4];

            normalized = normalized.Trim('/');
            if (normalized.Length == 0 || normalized.Contains(".."))
                return null;

            var parts = normalized.Split(new[] { '/' }, 2, StringSplitOptions.RemoveEmptyEntries).Select(p => p.Trim()).ToArray();
            var pack = parts.Length == 2 ? parts[0] : defaultPack;
            var name = parts.Length == 2 ? parts[1] : parts[0];

            if (!_IsSafeResourcePath(pack) || !_IsSafeResourcePath(name))
                return null;

            return new SvgIconKey(pack, name);
        }

        public override string ToString()
        {
            return $"{Pack}/{Name}";
        }

        private static bool _IsSafeResourcePath(string value)
        {
            if (string.IsNullOrWhiteSpace(value) || value[0] == '/' || value[value.Length - 1] == '/')
                return false;

            return value.Split(new[] { '/' }, StringSplitOptions.RemoveEmptyEntries).All(_IsSafeResourceSegment);
        }

        private static bool _IsSafeResourceSegment(string value)
        {
            if (string.IsNullOrWhiteSpace(value) || value is "." or "..")
                return false;

            return value.All(ch => char.IsLetterOrDigit(ch) || ch is '-' or '_' or '.');
        }
    }
}