#!/usr/bin/env python3
"""Apply the MidnightBeam Quick Controls addon to a fresh MadeinTaly/midnightbeam checkout.

Usage: python3 apply_to_upstream.py /path/to/midnightbeam
Does not need any third-party Python modules. Idempotent.
"""
from pathlib import Path
import re
import shutil
import sys

root = Path(__file__).resolve().parent
if len(sys.argv) != 2:
    raise SystemExit('Usage: python3 apply_to_upstream.py /path/to/midnightbeam')
dst = Path(sys.argv[1]).resolve()
manifest = dst / 'app' / 'AndroidManifest.xml'
source = dst / 'app' / 'src' / 'dev' / 'midnightbeam'
if not manifest.is_file() or not (source / 'DimService.java').is_file():
    raise SystemExit('Target is not a MidnightBeam source checkout (missing AndroidManifest.xml or DimService.java)')
for name in ('QuickPopupService.java', 'QuickPopupActivity.java'):
    shutil.copy2(root / 'app' / 'src' / 'dev' / 'midnightbeam' / name, source / name)
s = manifest.read_text(encoding='utf-8')
if '.QuickPopupActivity' not in s:
    declaration = '''
        <!-- Standalone shortcut entry: bind MidnightBeam Quick to Stickman. -->
        <activity
            android:name=".QuickPopupActivity"
            android:label="MidnightBeam Quick"
            android:exported="true"
            android:excludeFromRecents="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".QuickPopupService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="User-invoked remote-control filter popup" />
        </service>
'''
    if '</application>' not in s:
        raise SystemExit('Could not locate </application> in manifest')
    s = s.replace('</application>', declaration + '\n    </application>', 1)
if 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE' not in s:
    s = s.replace('</application>', '</application>', 1)
    anchor = '<application'
    if anchor not in s:
        raise SystemExit('Could not locate <application> in manifest')
    s = s.replace(anchor, '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n\n    ' + anchor, 1)
# Keep the upstream package name (the protected DimService requires same-app UID).
# Pick a high version code to avoid upstream APKs presenting as an upgrade.
s = re.sub(r'android:versionCode="\d+"', 'android:versionCode="199001"', s, count=1)
s = re.sub(r'android:versionName="([^"]+)"', lambda m: 'android:versionName="'+(m.group(1) if m.group(1).endswith('-quick') else m.group(1)+'-quick')+'"', s, count=1)
manifest.write_text(s, encoding='utf-8')
print('Applied Quick Controls addon to', dst)
print('Warning: custom APK is signed with your own key; it CANNOT update the existing official MidnightBeam install in place.')
