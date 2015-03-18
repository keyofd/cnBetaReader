package com.ywwxhz.processer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.loopj.android.http.TextHttpResponseHandler;
import com.melnykov.fab.FloatingActionButton;
import com.pnikosis.materialishprogress.ProgressWheel;
import com.ywwxhz.app.ImageViewActivity;
import com.ywwxhz.app.NewsCommentActivity;
import com.ywwxhz.app.fragment.FontSizeFragment;
import com.ywwxhz.cnbetareader.R;
import com.ywwxhz.entity.NewsItem;
import com.ywwxhz.lib.Configure;
import com.ywwxhz.lib.TranslucentStatus.TranslucentStatusHelper;
import com.ywwxhz.lib.handler.BaseProcesser;
import com.ywwxhz.lib.kits.FileCacheKit;
import com.ywwxhz.lib.kits.NetKit;
import com.ywwxhz.lib.kits.PrefKit;
import com.ywwxhz.lib.kits.Toolkit;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;
import org.apache.http.Header;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Created by ywwxhz on 2014/11/1.
 */
public class NewsDetailProcesser extends BaseProcesser {
    public static final String NEWS_ITEM_KEY = "key_news_item";
    private final TranslucentStatusHelper helper;
    private View vg;
    private View loadFail;
    private int margin = 0;
    private WebView mWebView;
    private Activity mContext;
    private boolean hascontent;
    private NewsItem mNewsItem;
    private ProgressWheel mProgressBar;
    private FloatingActionButton mActionButtom;

    private String webTemplate = "<!DOCTYPE html><html><head><title></title><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\"/>" +
            "<style>body{word-break: break-all;}video{width:100%% !important;height:auto !important}.content img{max-width: 100%% !important;height: auto; !important}a{text-decoration: none;color:#2f7cad;}" +
            ".content p iframe{width: 100%% !important;height:auto !important}.introduce img{padding:3pt;width:50pt;height:auto}.introduce p{margin:0}.introduce div{margin: 0px !important;}" +
            ".content embed{width: 100%% !important;height:auto; !important}.title{font-size: 18pt;color: #1473af;}" +
            ".from{font-size: 10pt;padding-top: 4pt;}.introduce{border: 1px solid #E5E5E5;background-color: #FBFBFB;font-size: 11pt;padding: 2pt;}" +
            ".content{padding-top:10pt;font-size: 12pt;}.clear{clear: both;}.foot{text-align: center;padding-top:10pt;padding-bottom: 20pt;}" +
            "</style></head><body><div><div class=\"title\">%s</div><div class=\"from\">%s<span style=\"float: right\">%s</span></div>" +
            "<hr style=\"clear: both\"/><div class=\"introduce\">%s<div style=\"clear: both\"></div></div><div class=\"content\">%s</div>" +
            "<div class=\"clear foot\">--- The End ---</div></div><script>var as = document.getElementsByTagName(\"a\");" +
            "for(var i=0;i<as.length;i++){var a = as[i];if(a.getElementsByTagName('img').length>0)" +
            "{a.onclick=function(){return false;}}}; function openImage(obj){window.Interface.showImage(obj.src);return false;}"+
            "</script></body></html>";
    private Handler myHandler;
    private WebSettings settings;

    public NewsDetailProcesser(Activity mContext, TranslucentStatusHelper helper) {
        this.hascontent = false;
        this.mContext = mContext;
        this.mContext.setContentView(R.layout.activity_detail);
        this.myHandler = new Handler();
        this.helper = helper;
        initView();
        if (mContext.getIntent().getExtras().containsKey(NEWS_ITEM_KEY)) {
            mNewsItem = (NewsItem) mContext.getIntent().getSerializableExtra(NEWS_ITEM_KEY);
            mContext.setTitle("详情：" + mNewsItem.getTitle());
            NewsItem mNews = FileCacheKit.getInstance().getAsObject(mNewsItem.getSid() + "", NewsItem.class);
            if (mNews == null) {
                makeRequest();
            } else {
                hascontent = true;
                mNewsItem = mNews;
                blindData(mNews);
            }
        } else {
            Toast.makeText(mContext, "缺少必要参数", Toast.LENGTH_SHORT).show();
            mContext.finish();
        }
    }

    private void blindData(NewsItem mNews) {
        String data = String.format(Locale.CHINA, webTemplate, mNews.getTitle(), mNews.getFrom(), mNews.getInputtime()
                , mNews.getHometext(), mNews.getContent());
        mWebView.loadDataWithBaseURL(null, data, "text/html", "utf-8", null);
        mWebView.setVisibility(View.VISIBLE);
        mActionButtom.postDelayed(new Runnable() {
            @Override
            public void run() {
                mActionButtom.setVisibility(View.VISIBLE);
                mActionButtom.animate().scaleX(1).scaleY(1).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
        }, 200);
        mProgressBar.setVisibility(View.GONE);
    }

    private void initView() {
        this.vg = mContext.findViewById(R.id.content);
        this.loadFail = mContext.findViewById(R.id.loadFail);
        this.mWebView = (WebView) mContext.findViewById(R.id.webview);
        this.mProgressBar = (ProgressWheel) mContext.findViewById(R.id.loading);
        this.mActionButtom = (FloatingActionButton) mContext.findViewById(R.id.action);
        this.mActionButtom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commentAction();
            }
        });
        this.helper.getOption().setConfigView(vg) ;
        mActionButtom.setScaleX(0);
        mActionButtom.setScaleY(0);
        settings = mWebView.getSettings();
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(true);
        settings.setPluginState(WebSettings.PluginState.ON_DEMAND);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        if (!PrefKit.getBoolean(mContext, mContext.getString(R.string.pref_hardware_accelerated_key), true)) {
            mWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        if (NetKit.isWifiConnected()) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
        mWebView.addJavascriptInterface(new JavaScriptInterface(mContext), "Interface");
        mWebView.setWebChromeClient(new WebChromeClient() {
            private View myView = null;
            private CustomViewCallback myCallback = null;
            private int orientation;

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (myCallback != null) {
                    myCallback.onCustomViewHidden();
                    myCallback = null;
                    return;
                }
                orientation = mContext.getRequestedOrientation();
                mContext.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                mContext.getActionBar().hide();
                helper.getOption().setWithActionBar(false);

                ViewGroup parent = (ViewGroup) mWebView.getParent();
                mWebView.setVisibility(View.GONE);
                mActionButtom.setVisibility(View.GONE);
                parent.addView(view);
                myView = view;
                myCallback = callback;
            }

            @Override
            public void onHideCustomView() {

                if (myView != null) {

                    if (myCallback != null) {
                        myCallback.onCustomViewHidden();
                        myCallback = null;
                    }
                    mContext.setRequestedOrientation(orientation);
                    mContext.getActionBar().show();
                    helper.getOption().setWithActionBar(true);
                    ViewGroup parent = (ViewGroup) myView.getParent();
                    parent.removeView(myView);
                    mWebView.setVisibility(View.VISIBLE);
                    mActionButtom.setVisibility(View.VISIBLE);

                    myView = null;
                }
            }


        });
        this.loadFail.setClickable(true);
        this.loadFail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeRequest();
            }
        });
    }

    public void makeRequest() {

        NetKit.getInstance().getNewsBySid(mNewsItem.getSid() + "", new TextHttpResponseHandler() {

            @Override
            public void onStart() {
                mProgressBar.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.GONE);
                loadFail.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                if (!hascontent) {
                    loadFail.setVisibility(View.VISIBLE);
                } else {
                    blindData(mNewsItem);
                    mWebView.setVisibility(View.VISIBLE);
                }
                mProgressBar.setVisibility(View.GONE);
                Toast.makeText(mContext, R.string.message_no_network, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                if (Configure.STANDRA_PATTERN.matcher(responseString).find()) {
                    new AsyncTask<String, Integer, Boolean>() {

                        @Override
                        protected Boolean doInBackground(String... params) {
                            Document doc = Jsoup.parse(params[0]);
                            Elements newsHeadlines = doc.select(".body");
                            mNewsItem.setFrom(newsHeadlines.select(".where").html());
                            mNewsItem.setHometext(newsHeadlines.select(".introduction").html());
                            Elements content = newsHeadlines.select(".content");
                            for(Element e:content.select("img")){
                                e.attr("onclick","openImage(this)");
                            }
                            mNewsItem.setContent(content.html());
                            Matcher snMatcher = Configure.SN_PATTERN.matcher(params[0]);
                            if (snMatcher.find())
                                mNewsItem.setSN(snMatcher.group(1));
                            hascontent = true;
                            FileCacheKit.getInstance().putAsync(mNewsItem.getSid() + "", Toolkit.getGson().toJson(mNewsItem), null);
                            return true;
                        }

                        @Override
                        protected void onPostExecute(Boolean result) {
                            if (result) {
                                blindData(mNewsItem);
                            }
                            mProgressBar.setVisibility(View.GONE);
                        }
                    }.execute(responseString);
                } else {
                    onFailure(statusCode, headers, responseString, new RuntimeException());
                }
            }

            @Override
            public void onProgress(int bytesWritten, int totalSize) {
            }
        });

    }

    public NewsItem getNewsItem() {
        return mNewsItem;
    }

    public Activity getContext() {
        return mContext;
    }

    public void setLoadFinish() {
        mProgressBar.setVisibility(View.GONE);
    }

    public WebView getWebView() {
        return mWebView;
    }

    public void fixPadding() {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mActionButtom.getLayoutParams();
        if (margin == 0) {
            margin = layoutParams.leftMargin;
        }
        layoutParams.bottomMargin = vg.getPaddingBottom() + margin;
        mActionButtom.setLayoutParams(layoutParams);
        vg.setPadding(vg.getPaddingLeft(), vg.getPaddingTop(), vg.getPaddingRight(), 0);
    }

    public void commentAction() {
        Intent intent = new Intent(mContext, NewsCommentActivity.class);
        intent.putExtra(NewsCommentProcesser.SN_KEY, mNewsItem.getSN());
        intent.putExtra(NewsCommentProcesser.SID_KEY, mNewsItem.getSid());
        intent.putExtra(NewsCommentProcesser.TITLE_KEY, mNewsItem.getTitle());
        mContext.startActivity(intent);
    }

    public void shareAction() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, mContext.getString(R.string.share_templates
                , mNewsItem.getTitle(), Configure.buildArticleUrl(mNewsItem.getSid() + "")));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.menu_share)));
    }

    public void viewInBrowser() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri content_url = Uri.parse(Configure.buildArticleUrl(mNewsItem.getSid() + ""));
        intent.setData(content_url);
        mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.choise_browser)));
    }

    public void handleFontSize() {
        FontSizeFragment fragment = FontSizeFragment.getInstance(settings.getTextZoom());
        fragment.show(mContext.getFragmentManager(), "Font Size");
        fragment.setSeekBarListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) {
                    settings.setTextZoom(value);
                }
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

            }
        });
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    private class JavaScriptInterface {
        Context mContext;

        JavaScriptInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showImage(final String imageSrc){
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(mContext, ImageViewActivity.class);
                    intent.putExtra(ImageViewActivity.IMAGE_URL,imageSrc);
                    mContext.startActivity(intent);
                }
            });
        }
    }
}