package ml.puredark.hviewer.activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.common.logging.FLog;
import com.facebook.datasource.DataSource;
import com.facebook.datasource.DataSubscriber;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.image.QualityInfo;
import com.gc.materialdesign.views.ProgressBarCircularIndeterminate;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.relex.photodraweeview.PhotoDraweeView;
import ml.puredark.hviewer.HViewerApplication;
import ml.puredark.hviewer.R;
import ml.puredark.hviewer.beans.Picture;
import ml.puredark.hviewer.beans.Site;
import ml.puredark.hviewer.customs.ExViewPager;
import ml.puredark.hviewer.customs.MultiTouchViewPager;
import ml.puredark.hviewer.helpers.HViewerHttpClient;
import ml.puredark.hviewer.helpers.MDStatusBarCompat;
import ml.puredark.hviewer.helpers.RuleParser;


public class PictureViewerActivity extends AppCompatActivity {

    @BindView(R.id.tv_count)
    TextView tvCount;
    @BindView(R.id.view_pager)
    MultiTouchViewPager viewPager;

    private PicturePagerAdapter picturePagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_viewer);
        ButterKnife.bind(this);
        MDStatusBarCompat.setImageTransparent(this);

        if (HViewerApplication.temp instanceof PicturePagerAdapter)
            picturePagerAdapter = (PicturePagerAdapter) HViewerApplication.temp;

        if (picturePagerAdapter == null || picturePagerAdapter.getCount() == 0) {
            Toast.makeText(this, "数据错误，请刷新后重试", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int position = getIntent().getIntExtra("position", 0);

        tvCount.setText((position + 1) + "/" + picturePagerAdapter.getCount());

        viewPager.setAdapter(picturePagerAdapter);

        ViewPager.OnPageChangeListener listener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                tvCount.setText((position + 1) + "/" + picturePagerAdapter.getCount());
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };
        viewPager.addOnPageChangeListener(listener);

        viewPager.setOffscreenPageLimit(5);

        viewPager.setCurrentItem(position);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public static class PicturePagerAdapter extends PagerAdapter {
        private Site site;
        public List<Picture> pictures;

        public PicturePagerAdapter(Site site, List<Picture> pictures) {
            this.site = site;
            this.pictures = pictures;
        }

        private View[] views = new View[1000];

        @Override
        public int getCount() {
            return pictures.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (views[position] != null) {
                container.removeView(views[position]);
                ImageView imageView = (ImageView) views[position].findViewById(R.id.iv_picture);
                if (imageView != null) {
                }
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = LayoutInflater.from(container.getContext()).inflate(R.layout.view_picture_viewer, null);
            final ImageView imageView = (ImageView) view.findViewById(R.id.iv_picture);
            final Picture picture = pictures.get(position);
            final ProgressBarCircularIndeterminate progressBar = (ProgressBarCircularIndeterminate) view.findViewById(R.id.progress_bar);
            if (site.picUrlSelector == null) {
                picture.pic = picture.url;
                loadImage(container.getContext(), picture, imageView, progressBar);
            } else if (picture.pic != null) {
                loadImage(container.getContext(), picture, imageView, progressBar);
            } else {
                getPictureUrl(container.getContext(), imageView, progressBar, picture, site);
            }
            views[position] = view;
            container.addView(view, 0);
            return view;
        }

        private void loadImage(Context context, Picture picture, final ImageView imageView, final ProgressBarCircularIndeterminate progressBar) {
            HViewerApplication.loadImageFromUrl(context, imageView, picture.pic, site.cookie, picture.referer,  new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, @Nullable ImageInfo imageInfo, @Nullable Animatable anim) {
                    super.onFinalImageSet(id, imageInfo, anim);
                    if (imageInfo == null) {
                        return;
                    }
                    progressBar.setVisibility(View.GONE);
                    if(imageView instanceof PhotoDraweeView) {
                        ((PhotoDraweeView)imageView).update(imageInfo.getWidth(), imageInfo.getHeight());
                    }
                }

                @Override
                public void onIntermediateImageSet(String id, @Nullable ImageInfo imageInfo) {
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    FLog.e(getClass(), throwable, "Error loading %s", id);
                    progressBar.setVisibility(View.GONE);
                }
            });
        }

        private void getPictureUrl(final Context context, final ImageView imageView, final ProgressBarCircularIndeterminate progressBar, final Picture picture, final Site site) {
            if (picture.url.endsWith(".jpg") || picture.url.endsWith(".png") || picture.url.endsWith(".bmp")) {
                picture.pic = picture.url;
                loadImage(context, picture, imageView, progressBar);
            } else
                HViewerHttpClient.get(picture.url, site.getCookies(), new HViewerHttpClient.OnResponseListener() {

                    @Override
                    public void onSuccess(String contentType, Object result) {
                        if (result == null || result.equals(""))
                            return;
                        if (contentType.contains("image") && result instanceof Bitmap) {
                            picture.pic = picture.url;
                            imageView.setImageBitmap((Bitmap) result);
                            progressBar.setVisibility(View.GONE);
                        } else {
                            picture.pic = RuleParser.getPictureUrl((String) result, site.picUrlSelector, picture.url);
                            picture.retries = 0;
                            picture.referer = picture.url;
                            loadImage(context, picture, imageView, progressBar);
                        }
                    }

                    @Override
                    public void onFailure(HViewerHttpClient.HttpError error) {
                        if (picture.retries < 15) {
                            picture.retries++;
                            getPictureUrl(context, imageView, progressBar, picture, site);
                        } else {
                            picture.retries = 0;
                        }
                    }
                });
        }
    }

}
