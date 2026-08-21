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
manifest = manifest.replace(/<application\s*\n/, '<application\n        android:hardwareAccelerated="true"\n');
write('app/src/main/AndroidManifest.xml', manifest);

let strings = read('app/src/main/res/values/strings.xml');
strings = strings.replace(/(<string name="app_name">)[^<]*(<\/string>)/, '$1ADMIN$2');
strings = strings.replace(/(<string name="title_activity_main">)[^<]*(<\/string>)/, '$1ADMIN$2');
write('app/src/main/res/values/strings.xml', strings);

let activity = read('app/src/main/java/com/abdulasif/pdtstockscanner/MainActivity.java');
activity = activity.replace('import android.os.Bundle;\n', 'import android.os.Bundle;\nimport android.os.Build;\n');
activity = activity.replace('import com.abdulasif.pdtstockscanner.bridge.UpdateBridge;\n', '');
activity = activity.replace('    private UpdateBridge updateBridge;\n', '');
activity = replaceOnce(
  activity,
  '        super.onCreate(savedInstanceState);\n',
  '        super.onCreate(savedInstanceState);\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            getWindow().setFrameRate(120f, android.view.Window.FRAME_RATE_COMPATIBILITY_DEFAULT, android.view.Window.CHANGE_FRAME_RATE_ALWAYS);\n        }\n',
  'Admin high-refresh display request'
);
activity = replaceOnce(
  activity,
  '        updateBridge = new UpdateBridge(this);\n        webView.addJavascriptInterface(updateBridge, "UpdateBridge");\n',
  '        // Private admin APK: update bridge intentionally omitted.\n',
  'UpdateBridge registration'
);
activity = replaceOnce(
  activity,
  '        webView.setBackgroundColor(Color.rgb(11, 13, 20));\n',
  '        webView.setBackgroundColor(Color.rgb(11, 13, 20));\n        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);\n',
  'Admin hardware WebView layer'
);
activity = replaceOnce(
  activity,
  '        settings.setCacheMode(WebSettings.LOAD_DEFAULT);\n',
  '        settings.setCacheMode(WebSettings.LOAD_DEFAULT);\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {\n            settings.setOffscreenPreRaster(true);\n        }\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);\n        }\n',
  'Admin WebView performance settings'
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
html = replaceOnce(
  html,
  "function renderSettings(){\n  let html = '<div style=\"padding:14px 16px 10px;\">';",
  "function renderSettings(){\n  let html = '<div class=\"admin-private-settings\" style=\"padding:12px 14px 18px;\">';\n  html += '<div class=\"admin-private-settings-header\"><div><div class=\"admin-private-settings-kicker\">ADMIN CONTROL CENTER</div><div class=\"admin-private-settings-title\">Settings</div><div class=\"admin-private-settings-subtitle\">Manage the administrator workspace and security controls</div></div><div class=\"admin-private-settings-badge\">ADMIN</div></div>';",
  'Admin Settings wrapper'
);
html = html.replace('onclick="toggleAuditLogView()"', 'onclick="openAdminWindow(&#39;audit&#39;)"');
html = html.replace('onclick="toggleUserMgmt()"', 'onclick="openAdminWindow(&#39;users&#39;)"');
html = html.replace('onclick="if(!state.appLocked)toggleAppLock();"', 'onclick="if(!state.appLocked)adminConfirmPromoterAction(&#39;lock&#39;);"');
html = html.replace('onclick="if(state.appLocked)toggleAppLock();"', 'onclick="if(state.appLocked)adminConfirmPromoterAction(&#39;unlock&#39;);"');
html = html.replace('onclick="forceLogoutPromoters();"', 'onclick="adminConfirmPromoterAction(&#39;logout&#39;);"');
html = html.replace('    if(state.auditViewOpen) html += renderAuditLog();\n', '');
html = html.replace('    if(state.userMgmtOpen) html += renderUserMgmt();\n', '');

const adminStyle = String.raw`
.admin-private-settings{max-width:920px;margin:0 auto;}
.admin-private-settings-header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:4px 2px 14px;}
.admin-private-settings-kicker{font-size:10px;letter-spacing:1.25px;font-weight:900;color:var(--accent);}
.admin-private-settings-title{font-size:25px;line-height:1.1;font-weight:950;letter-spacing:-.6px;margin-top:3px;}
.admin-private-settings-subtitle{font-size:11px;color:var(--muted);margin-top:5px;line-height:1.35;}
.admin-private-settings-badge{font-size:10px;font-weight:900;letter-spacing:.8px;color:var(--accent);border:1px solid color-mix(in srgb,var(--accent) 45%,transparent);border-radius:999px;padding:6px 9px;white-space:nowrap;background:color-mix(in srgb,var(--accent) 10%,transparent);}
.admin-private-settings .ios-section-title{margin:18px 2px 7px;font-size:10px;letter-spacing:1px;font-weight:900;text-transform:uppercase;color:var(--muted);}
.admin-private-settings .ios-group{margin-bottom:10px;border:1px solid color-mix(in srgb,var(--ui-border,var(--border2)) 72%,transparent);box-shadow:0 8px 22px rgba(0,0,0,.12);}
.admin-private-settings .bento-grid{margin-bottom:10px;gap:8px;}
.admin-private-settings .bento-tile{min-height:58px;padding:11px 12px;}
.admin-private-settings .ios-row{min-height:54px;}
.admin-private-settings .ios-row-controls{gap:6px;max-width:62%;}
.admin-private-settings .ios-row-controls input,.admin-private-settings .ios-row-controls select{max-width:150px;}
.admin-private-settings .promoter-actions-card{margin:0 0 8px;border:1px solid color-mix(in srgb,var(--accent) 30%,var(--ui-border,var(--border2)));box-shadow:none;}
.admin-private-settings > .btn:last-child{margin-top:18px!important;}
.admin-private-window-box{width:min(96vw,760px);max-width:760px;height:min(88vh,900px);max-height:88vh;padding:16px;text-align:left;display:flex;flex-direction:column;overflow:hidden;}
.admin-private-window-header{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:0 34px 12px 2px;border-bottom:1px solid var(--ui-border,var(--border2));flex:0 0 auto;}
.admin-private-window-title{font-size:18px;font-weight:900;letter-spacing:-.2px;}
.admin-private-window-subtitle{font-size:11px;color:var(--muted);margin-top:3px;}
.admin-private-window-content{overflow:auto;-webkit-overflow-scrolling:touch;overscroll-behavior:contain;contain:content;flex:1;min-height:0;padding:12px 2px 4px;}
.admin-private-window-content .form-card{margin-top:0!important;}
.admin-private-window-footer{display:flex;justify-content:flex-end;padding-top:10px;flex:0 0 auto;}
.admin-private-smooth .ios-row,.admin-private-smooth .btn,.admin-private-smooth .card{backface-visibility:hidden;-webkit-backface-visibility:hidden;}
@media (max-width:560px){.admin-private-settings{padding-left:10px!important;padding-right:10px!important;}.admin-private-settings-header{padding-left:2px;padding-right:2px;}.admin-private-settings-title{font-size:22px;}.admin-private-settings-subtitle{max-width:245px;}.admin-private-settings .ios-row-controls{max-width:58%;}.admin-private-settings .ios-row-controls input{width:92px!important;}.admin-private-window-box{width:calc(100vw - 18px);height:min(90vh,900px);padding:14px 12px;}.admin-private-window-content{padding-left:0;padding-right:0;}}
`;
const styleAt = html.lastIndexOf('</style>');
if(styleAt < 0) throw new Error('Expected closing style tag for Admin window styles was not found');
html = html.slice(0, styleAt) + adminStyle + html.slice(styleAt);

const adminWindowScript = String.raw`
<script>
(function(){
  var activeAdminWindow = null;
  function ensureAdminWindow(){
    if(document.getElementById('adminPrivateWindowModal')) return;
    document.body.insertAdjacentHTML('beforeend',
      '<div class="modal-overlay" id="adminPrivateWindowModal" onclick="if(event.target===this)closeAdminWindow()">' +
      '<div class="modal-box modal-box-dark admin-private-window-box">' +
      '<div class="admin-private-window-header"><div><div id="adminPrivateWindowTitle" class="admin-private-window-title">Admin</div><div id="adminPrivateWindowSubtitle" class="admin-private-window-subtitle">Private administrator window</div></div><button class="modal-close" style="color:var(--text);top:10px;right:10px;" onclick="closeAdminWindow()" aria-label="Close">&times;</button></div>' +
      '<div id="adminPrivateWindowContent" class="admin-private-window-content"></div>' +
      '<div class="admin-private-window-footer"><button class="btn btn-ghost btn-sm" onclick="closeAdminWindow()">Close</button></div>' +
      '</div></div>'
    );
  }
  function refreshAdminWindow(){
    if(!activeAdminWindow) return;
    var content = document.getElementById('adminPrivateWindowContent');
    if(!content) return;
    var body = activeAdminWindow === 'audit' ? renderAuditLog() : renderUserMgmt();
    content.innerHTML = body || '<div class="empty">Nothing to show.</div>';
    if(typeof hydrateLucideIcons === 'function') hydrateLucideIcons(content);
    if(typeof replaceVisibleEmojiIcons === 'function') replaceVisibleEmojiIcons(content);
  }
  window.openAdminWindow = function(kind){
    if(typeof isAdminUser === 'function' && !isAdminUser()){ showToast('Only Admin can open this window', true); return; }
    activeAdminWindow = kind === 'users' ? 'users' : 'audit';
    ensureAdminWindow();
    var modal = document.getElementById('adminPrivateWindowModal');
    var title = document.getElementById('adminPrivateWindowTitle');
    var subtitle = document.getElementById('adminPrivateWindowSubtitle');
    if(title) title.textContent = activeAdminWindow === 'audit' ? 'Audit Log' : 'User Management';
    if(subtitle) subtitle.textContent = activeAdminWindow === 'audit' ? 'Review administrator activity' : 'Manage staff and promoter accounts';
    refreshAdminWindow();
    if(modal) modal.classList.add('show');
    document.body.classList.add('admin-private-smooth');
  };
  window.closeAdminWindow = function(){
    var modal = document.getElementById('adminPrivateWindowModal');
    if(modal) modal.classList.remove('show');
    activeAdminWindow = null;
    state.auditViewOpen = false;
    state.userMgmtOpen = false;
    document.body.classList.remove('admin-private-smooth');
    if(typeof render === 'function') render();
  };
  window.toggleAuditLogView = function(){ window.openAdminWindow('audit'); };
  window.toggleUserMgmt = function(){ window.openAdminWindow('users'); };
  window.adminConfirmPromoterAction = function(action){
    if(typeof isAdminUser === 'function' && !isAdminUser()){ showToast('Only Admin can control promoters', true); return; }
    var expected = String((state.appUsers && state.appUsers['Asif']) || '');
    if(!expected){ showToast('Admin password is unavailable', true); return; }
    state._adminProtectedAction = action;
    openActionModal(
      '<div style="font-weight:800;font-size:18px;margin-bottom:10px;">Admin Password Required</div>' +
      '<div style="font-size:13px;color:var(--muted);line-height:1.5;margin-bottom:12px;">Enter the Admin password to ' + esc(action === 'logout' ? 'log out all promoters' : (action === 'lock' ? 'lock promoter login' : 'unlock promoter login')) + '.</div>' +
      '<input id="adminProtectedPassword" type="password" autocomplete="current-password" placeholder="Admin password" style="width:100%;box-sizing:border-box;margin-bottom:14px;" onkeydown="if(event.key===\'Enter\')submitAdminProtectedAction()" />' +
      '<div style="display:flex;gap:8px;justify-content:flex-end;"><button class="btn btn-ghost" onclick="state._adminProtectedAction=null;closeActionModal()">Cancel</button><button class="btn btn-accent" onclick="submitAdminProtectedAction()">Confirm</button></div>'
    );
    setTimeout(function(){ var input=document.getElementById('adminProtectedPassword'); if(input) input.focus(); }, 50);
  };
  window.submitAdminProtectedAction = function(){
    var action = state._adminProtectedAction;
    var expected = String((state.appUsers && state.appUsers['Asif']) || '');
    var input = document.getElementById('adminProtectedPassword');
    if(!action) return;
    if(!input || String(input.value || '') !== expected){ showToast('Incorrect Admin password', true); if(input){ input.value=''; input.focus(); } return; }
    state._adminProtectedAction = null;
    closeActionModal();
    state._adminProtectedActionVerified = true;
    if(action === 'lock' && !state.appLocked) toggleAppLock();
    else if(action === 'unlock' && state.appLocked) toggleAppLock();
    else if(action === 'logout') forceLogoutPromoters();
    else state._adminProtectedActionVerified = false;
  };
  var originalToggleAppLock = window.toggleAppLock;
  if(typeof originalToggleAppLock === 'function'){
    window.toggleAppLock = async function(){
      if(!state._adminProtectedActionVerified){ adminConfirmPromoterAction(state.appLocked ? 'unlock' : 'lock'); return; }
      state._adminProtectedActionVerified = false;
      return originalToggleAppLock.apply(this, arguments);
    };
  }
  var originalForceLogoutPromoters = window.forceLogoutPromoters;
  if(typeof originalForceLogoutPromoters === 'function'){
    window.forceLogoutPromoters = async function(){
      if(!state._adminProtectedActionVerified){ adminConfirmPromoterAction('logout'); return; }
      state._adminProtectedActionVerified = false;
      return originalForceLogoutPromoters.apply(this, arguments);
    };
  }
  var originalRender = window.render;
  if(typeof originalRender === 'function'){
    window.render = function(){
      var result = originalRender.apply(this, arguments);
      if(activeAdminWindow) refreshAdminWindow();
      return result;
    };
  }
  var originalHandleNativeBack = window.handleNativeBack;
  window.handleNativeBack = function(){
    var modal = document.getElementById('adminPrivateWindowModal');
    if(modal && modal.classList.contains('show')){ window.closeAdminWindow(); return true; }
    return typeof originalHandleNativeBack === 'function' ? originalHandleNativeBack.apply(this, arguments) : false;
  };
})();
</script>`;
const bodyAt = html.lastIndexOf('</body>');
if(bodyAt < 0) throw new Error('Expected closing body tag for Admin enhancements was not found');
html = html.slice(0, bodyAt) + adminWindowScript + '\n' + html.slice(bodyAt);
html = html.replace('<body>', '<body class="admin-private-smooth">');
write('app/src/main/assets/public/index.html', html);

console.log(`Prepared private admin APK variant at ${sourceRoot}`);
