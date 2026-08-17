import fs from 'node:fs';
import path from 'node:path';

const sourceRoot = path.resolve(process.argv[2] || 'FHL_NATIVE_APP');
const runNumber = Number(process.env.GITHUB_RUN_NUMBER || 1);
const privateVersion = process.env.ADMIN_PRIVATE_VERSION || '6.1-admin-private';
const privatePackage = 'com.abdulasif.pdtstockscanner.admin';

function read(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8');
}

function write(relativePath, content) {
  fs.writeFileSync(path.join(sourceRoot, relativePath), content);
}

function replaceOnce(content, find, replacement, label) {
  const at = content.indexOf(find);
  if (at < 0) throw new Error(`Expected ${label} was not found`);
  return content.slice(0, at) + replacement + content.slice(at + find.length);
}

let gradle = read('app/build.gradle');
gradle = gradle.replace(/applicationId\s+"[^"]+"/, `applicationId "${privatePackage}"`);
gradle = gradle.replace(/versionCode\s+\d+/, `versionCode ${100000 + runNumber}`);
gradle = gradle.replace(/versionName\s+"[^"]+"/, `versionName "${privateVersion}"`);
write('app/build.gradle', gradle);

let manifest = read('app/src/main/AndroidManifest.xml');
manifest = manifest.replace(/\s*<uses-permission android:name="android\.permission\.REQUEST_INSTALL_PACKAGES"\s*\/\>\s*\n/, '\n');
manifest = manifest.replace(/android:authorities="[^"]+"/, `android:authorities="${privatePackage}.fileprovider"`);
write('app/src/main/AndroidManifest.xml', manifest);

let strings = read('app/src/main/res/values/strings.xml');
strings = strings.replace(/(<string name="app_name">)[^<]*(<\/string>)/, '$1ADMIN$2');
strings = strings.replace(/(<string name="title_activity_main">)[^<]*(<\/string>)/, '$1ADMIN$2');
write('app/src/main/res/values/strings.xml', strings);

let activity = read('app/src/main/java/com/abdulasif/pdtstockscanner/MainActivity.java');
activity = activity.replace('import com.abdulasif.pdtstockscanner.bridge.UpdateBridge;\n', '');
activity = activity.replace('    private UpdateBridge updateBridge;\n', '');
activity = replaceOnce(
  activity,
  '        updateBridge = new UpdateBridge(this);\n        webView.addJavascriptInterface(updateBridge, "UpdateBridge");\n',
  '        // Private admin APK: update bridge intentionally omitted.\n',
  'UpdateBridge registration'
);
write('app/src/main/java/com/abdulasif/pdtstockscanner/MainActivity.java', activity);

let html = read('app/src/main/assets/public/index.html');
html = replaceOnce(
  html,
  '<head>',
  '<head>\n<script>window.__FHL_ADMIN_PRIVATE_BUILD__=true;</script>',
  'HTML head'
);
html = replaceOnce(
  html,
  "if(foundKey === 'Asif' && users[foundKey] !== password){",
  "if(!window.__FHL_ADMIN_PRIVATE_BUILD__ && foundKey === 'Asif' && users[foundKey] !== password){",
  'administrator preview login condition'
);
html = replaceOnce(
  html,
  "  const roleOfFinder = (foundKey === 'Asif') ? 'admin' : ((state.userRoles && state.userRoles[foundKey] && state.userRoles[foundKey].role) ? state.userRoles[foundKey].role : 'staff');\n",
  "  const roleOfFinder = (foundKey === 'Asif') ? 'admin' : ((state.userRoles && state.userRoles[foundKey] && state.userRoles[foundKey].role) ? state.userRoles[foundKey].role : 'staff');\n  if(window.__FHL_ADMIN_PRIVATE_BUILD__ && roleOfFinder !== 'admin'){ showToast('This private APK is for administrator accounts only.', true); return; }\n",
  'administrator-only login guard'
);
html = replaceOnce(
  html,
  'function canUseNativeUpdateFlow(){',
  'function canUseNativeUpdateFlow(){\n  if(window.__FHL_ADMIN_PRIVATE_BUILD__) return false;',
  'native update flow guard'
);
html = html.replace(/const APP_VERSION = '[^']+';/, `const APP_VERSION = '${privateVersion}';`);
html = html.replace(/const APP_BUILD = '[^']+';/, `const APP_BUILD = 'private-${runNumber}';`);
write('app/src/main/assets/public/index.html', html);

console.log(`Prepared private admin APK variant at ${sourceRoot}`);
