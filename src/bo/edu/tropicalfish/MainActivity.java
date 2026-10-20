package bo.edu.tropicalfish;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Tropical Fish Acuario — Contenedor WebView del sistema web.
 * Carga el sistema (PHP/MVC) hospedado en el servidor del local
 * (IP de red local) o publicado mediante túnel seguro (ngrok/HTTPS).
 */
public class MainActivity extends Activity {

    private static final String PREFS = "tf_prefs";
    private static final String KEY_URL = "server_url";
    private static final String URL_POR_DEFECTO = "https://tropical-fish.onrender.com/login";
    private static final int REQ_ARCHIVO = 1001;

    private WebView web;
    private SharedPreferences prefs;
    private FrameLayout raiz;
    private ValueCallback<Uri[]> subidaCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.parseColor("#04182E"));
        setContentView(raiz);

        cargar(prefs.getString(KEY_URL, URL_POR_DEFECTO));
    }

    /** Pantalla de configuración: pide la URL del servidor. */
    private void mostrarConfig() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        panel.setPadding(pad, pad, pad, pad);

        TextView titulo = new TextView(this);
        titulo.setText(getString(R.string.titulo_config));
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(22);
        titulo.setPadding(0, 0, 0, dp(12));

        TextView desc = new TextView(this);
        desc.setText("Ingrese la dirección del servidor del acuario:");
        desc.setTextColor(Color.parseColor("#9FC5E8"));
        desc.setPadding(0, 0, 0, dp(16));

        EditText campo = new EditText(this);
        campo.setHint(getString(R.string.hint_url));
        campo.setHintTextColor(Color.parseColor("#7FA8C9"));
        campo.setTextColor(Color.WHITE);
        campo.setSingleLine(true);
        campo.setText(prefs.getString(KEY_URL, URL_POR_DEFECTO));

        Button btn = new Button(this);
        btn.setText(getString(R.string.btn_conectar));

        TextView nota = new TextView(this);
        nota.setText("En el local use la IP del equipo (http://192.168.0.5:8080).\nFuera del local use la URL del túnel seguro (ngrok).");
        nota.setTextColor(Color.parseColor("#7FA8C9"));
        nota.setTextSize(13);
        nota.setPadding(0, dp(20), 0, 0);

        panel.addView(titulo);
        panel.addView(desc);
        panel.addView(campo);
        panel.addView(btn);
        panel.addView(nota);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        raiz.removeAllViews();
        raiz.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btn.setOnClickListener(v -> {
            String url = campo.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Ingrese la URL del servidor", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http")) url = "http://" + url;
            if (!url.endsWith("/")) url += "/";
            prefs.edit().putString(KEY_URL, url).apply();
            cargar(url);
        });
    }

    /** Carga el sistema web en el WebView a pantalla completa. */
    private void cargar(String url) {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
                // Solo mostrar error si falla la página principal
                if (failingUrl != null && failingUrl.equals(view.getUrl())) {
                    mostrarError();
                }
            }
        });

        // Necesario para la importación de archivos CSV (input type=file)
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                subidaCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "Seleccionar archivo"), REQ_ARCHIVO);
                return true;
            }
        });

        // Descargas: exportación de reportes CSV
        web.setDownloadListener((urlDesc, ua, contentDisp, mime, len) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(urlDesc));
                req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(urlDesc));
                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String nombre = URLUtil.guessFileName(urlDesc, contentDisp, mime);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombre);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(this, "Descargando " + nombre, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo descargar el archivo", Toast.LENGTH_SHORT).show();
            }
        });

        raiz.removeAllViews();
        raiz.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(url);
    }

    /** Pantalla de error de conexión con reintento. */
    private void mostrarError() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER);
        int pad = dp(30);
        panel.setPadding(pad, pad, pad, pad);

        TextView msg = new TextView(this);
        msg.setText(getString(R.string.error_conexion));
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(16);

        Button reintentar = new Button(this);
        reintentar.setText("Reintentar");
        reintentar.setOnClickListener(v -> cargar(prefs.getString(KEY_URL, URL_POR_DEFECTO)));

        Button cambiar = new Button(this);
        cambiar.setText(getString(R.string.menu_cambiar));
        cambiar.setOnClickListener(v -> mostrarConfig());

        panel.addView(msg);
        panel.addView(reintentar);
        panel.addView(cambiar);
        raiz.removeAllViews();
        raiz.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Respuesta del selector de archivos (importación CSV)
        if (requestCode == REQ_ARCHIVO && subidaCallback != null) {
            Uri[] resultado = null;
            if (resultCode == RESULT_OK && data != null) {
                String uriStr = data.getDataString();
                if (uriStr != null) resultado = new Uri[]{Uri.parse(uriStr)};
            }
            subidaCallback.onReceiveValue(resultado);
            subidaCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, getString(R.string.menu_recargar));
        menu.add(0, 2, 0, getString(R.string.menu_cambiar));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1 && web != null) {
            web.reload();
            return true;
        }
        if (item.getItemId() == 2) {
            mostrarConfig();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
