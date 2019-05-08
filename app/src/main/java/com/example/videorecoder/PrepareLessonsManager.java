//package com.example.videorecoder;
//
//import android.arch.lifecycle.Observer;
//import android.content.Intent;
//import android.database.Observable;
//import android.graphics.Bitmap;
//import android.graphics.Matrix;
//import android.net.Uri;
//import android.os.Build;
//import android.provider.MediaStore;
//import android.support.annotation.NonNull;
//import android.support.v4.content.FileProvider;
//import android.util.Log;
//
//
//
//import java.io.BufferedOutputStream;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//
///**
// * Created by zhaohengyu .
// * Date 2016/11/1 0001.
// * 老师在平板临时备课处理
// */
//
//public class PrepareLessonsManager {
//    private OnlineClassActivity mActivity;
//    public static final int CODE_TAKEPHOTO = 0x1;//拍照请求码
//    private String mPhotoTempPath;//缓存拍照图片的地址
//    private LekeProgressDialog mDialog;
//
//    private RecordVideoDialogActivity mRecordVideoDialog;
//    private boolean isShowRecordVideoDialog;
//    private boolean mHasClosedMediaRecord;
//
//    private String mTempImagePath; // 相机临时文件路径
//    private RecordPhotoDialog mPhotoDialog;
//
//
//    public PrepareLessonsManager(OnlineClassActivity activity) {
//        this.mActivity = activity;
//    }
//
//    public String getFileName() {
//        String lessonName = mActivity.getClassName();
//        SimpleDateFormat format = new SimpleDateFormat("MMdd_HHmmss_SSS", Locale.getDefault());
//        if (!lessonName.contains("未命名课堂")) {
//            return lessonName + format.format(new Date(System.currentTimeMillis()));
//        } else {
//            String[] s = lessonName.split("未命名课堂");
//            return s[0] + "未命名课堂" + format.format(new Date(System.currentTimeMillis()));
//        }
//    }
//
//    public String getFileName(long time) {
//        String lessonName = mActivity.getClassName();
//        SimpleDateFormat format = new SimpleDateFormat("MMdd_HHmmss_SSS", Locale.getDefault());
//        if (!lessonName.contains("未命名课堂")) {
//            return lessonName + format.format(new Date(time));
//        } else {
//            String[] s = lessonName.split("未命名课堂");
//            return s[0] + "未命名课堂" + format.format(new Date(time));
//        }
//    }
//
//    /**
//     * 拍照
//     */
//    public void takePhoto() {
//        Intent intent = new Intent();
//        // 指定开启系统相机的Action
//        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
//        // 根据文件地址创建文件
//        File dir = new File(LekeSettings.getTempPath());
//        File file = new File(dir, getFileName() + ".jpg");
//        try {
//            if (!file.exists()) {
//                boolean suc = file.createNewFile();
//                if (!suc) {
//                    ToastUtils.shortShow(mActivity, "创建文件失败");
//                    return;
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            ToastUtils.shortShow(mActivity, "创建文件失败");
//            return;
//        }
//        mPhotoTempPath = file.getPath();
//        // 把文件地址转换成Uri格式
//        Uri uri = FileProvider.getUriForFile(mActivity, file);
//        // 设置系统相机拍摄照片完成后图片文件的存放地址
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
//        mActivity.startActivityForResult(intent, CODE_TAKEPHOTO);
//    }
//
//    /**
//     * 短视频
//     */
//    public void takeVideo() {
//        int style = 0;
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//            style = R.style.show_student_screen_dialog;
//        } else {
//            style = R.style.record_dialog_style;
//        }
//        mRecordVideoDialog = new RecordVideoDialogActivity(mActivity, this, style);
//        mRecordVideoDialog.show();
//        isShowRecordVideoDialog = true;
//    }
//
//    public String creatDefPhotoPath(long time) {
//        // 根据文件地址创建文件
//        File dir = new File(LekeSettings.getTempPath());
//        File file = new File(dir, getFileName(time) + ".jpg");
//        mPhotoTempPath = file.getPath();
//        return mPhotoTempPath;
//    }
//
//    private synchronized String scaleBitmap(Bitmap bm, long time) throws IOException {
//        Bitmap bitmap = bm;
//        int screenH = LekeSettings.getDisplayHeight();
//        int h = bitmap.getHeight();
//        float wScale = ((float) screenH) / h;
//        Matrix matrix = new Matrix();
//        matrix.postScale(wScale, wScale);
//        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
//        File file;
//        final String defPhotoPath = creatDefPhotoPath(time);
//        file = new File(defPhotoPath);
//        file.delete();
//        file.createNewFile();
//        FileOutputStream fileOutputStream = new FileOutputStream(file);
//        BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos);
//        bos.flush();
//        bos.close();
//        return defPhotoPath;
//    }
//
//    /**
//     * 处理图片课件
//     */
//    public void dealwithPhoto(final HashMap<Long, Bitmap> bitmaps) {
//        ArrayList<Observable<String>> dealPhotoObservables = new ArrayList<>();
//        for (Map.Entry<Long, Bitmap> entry : bitmaps.entrySet()) {
//            dealPhotoObservables.add(Observable.just(entry).flatMap(new Function<Map.Entry<Long, Bitmap>, ObservableSource<String>>() {
//
//                @Override
//                public ObservableSource<String> apply(@NonNull Map.Entry<Long, Bitmap> longBitmapEntry) throws Exception {
//                    final String path = scaleBitmap(longBitmapEntry.getValue(), longBitmapEntry.getKey());
//                    return new Observable<String>() {
//                        @Override
//                        protected void subscribeActual(Observer<? super String> observer) {
//                            observer.onNext(path);
//                        }
//                    };
//                }
//            }));
//        }
//        Observable.zip(dealPhotoObservables, new Function<Object[], List<String>>() {
//            @Override
//            public List<String> apply(@NonNull Object[] objects) throws Exception {
//                List<String> paths = new ArrayList<String>();
//                for (Object path : objects) {
//                    paths.add((String) path);
//                }
//                return paths;
//            }
//        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List<String>>() {
//            @Override
//            public void onSubscribe(@NonNull Disposable d) {
//
//            }
//
//            @Override
//            public void onNext(@NonNull List<String> s) {
//                uploadFile(s, false);
//            }
//
//            @Override
//            public void onError(@NonNull Throwable e) {
//                Log.e("PrepareLessonsManager", "[onError]：处理图片失败");
//                e.printStackTrace();
//                ToastUtils.shortShow(mActivity, "处理图片失败");
//            }
//
//            @Override
//            public void onComplete() {
//
//            }
//        });
//    }
//
//    private class FlatFun implements Function<Result<TransResponse>, ObservableSource<TransResponse>> {
//
//        @Override
//        public ObservableSource<TransResponse> apply(@NonNull final Result<TransResponse> transResponseResult) throws Exception {
//            return Observable.create(new ObservableOnSubscribe<TransResponse>() {
//                @Override
//                public void subscribe(@NonNull ObservableEmitter<TransResponse> e) throws Exception {
//                    if (transResponseResult.success) {
//                        e.onNext(transResponseResult.datas);
//                    } else {
//                        e.onError(new IllegalStateException("资源上传失败"));
//                    }
//                    e.onComplete();
//                }
//            });
//        }
//    }
//
//    private FlatFun mFlatFun = new FlatFun();
//
//    /**
//     * 上传课件
//     *
//     * @param paths   文件路径
//     * @param isMedia 是多媒体
//     */
//    public void uploadFile(final List<String> paths, final boolean isMedia) {
//        FileTransApi transApi = HttpClient.getInstance().createService(FileTransApi.class);
//        List<Observable<TransResponse>> observables = new ArrayList<>();
//        for (String s : paths) {
//            RequestBody priority = RequestBody.create(MediaType.parse("multipart/form-data"), "9");
//            RequestBody appId = RequestBody.create(MediaType.parse("multipart/form-data"), "courseware");
//            UserBean shareUser = ShareDataHelper.getInstance().getShareUser();
//            RequestBody userId = RequestBody.create(MediaType.parse("multipart/form-data"), String.valueOf(shareUser.getId()));
//            RequestBody schoolId = RequestBody.create(MediaType.parse("multipart/form-data"), String.valueOf(shareUser.getCurrentSchool().getId()));
//            File f = new File(s);
//            RequestBody file = RequestBody.create(MediaType.parse("multipart/form-data"), f);
//            MultipartBody.Part body = MultipartBody.Part.createFormData("file", f.getName(), file);
//            Observable<TransResponse> observable = transApi.trans(body, priority, appId, userId, schoolId).flatMap(mFlatFun);
//            observables.add(observable);
//        }
//
//        Observable.zip(observables, new Function<Object[], UploadResultData>() {
//            @Override
//            public UploadResultData apply(@NonNull Object[] objects) throws Exception {
//                List<TransResponse> responses = new ArrayList<>();
//                for (Object o : objects) {
//                    TransResponse transResponse = (TransResponse) o;
//                    responses.add(transResponse);
//                    sendUpdateEvent(transResponse, isMedia);
//                }
//                if (isMedia) {
//                    return new UploadResultData(mRecordVideoDialog.getEndFrame(), true);
//                } else {
//                    return new UploadResultData(null, false);
//                }
//            }
//        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<UploadResultData>() {
//            @Override
//            public void onSubscribe(@NonNull Disposable d) {
//                if (mDialog == null) {
//                    mDialog = new LekeProgressDialog(mActivity);
//                }
//                mDialog.setMessage(R.string.is_uploading);
//                mDialog.show();
//            }
//
//            @Override
//            public void onNext(@NonNull UploadResultData uploadData) {
//                Log.i("PrepareLessonsManager", "[onNext]：课件绑定完成");
//                mDialog.dismiss();
//                if (isMedia) {
//                    showAnimal(uploadData.bitmap);
//                }
//                if (mRecordVideoDialog != null) {
//                    mRecordVideoDialog.dismiss();
//                    mRecordVideoDialog = null;
//                }
//                isShowRecordVideoDialog = false;
//                ToastUtils.longShow(mActivity, "课件上传成功");
//            }
//
//            @Override
//            public void onError(@NonNull Throwable e) {
//                Log.e("PrepareLessonsManager", "[onError]：" + e.getMessage());
//                e.printStackTrace();
//                mDialog.dismiss();
//                ToastUtils.longShow(mActivity, "上传失败");
//            }
//
//            @Override
//            public void onComplete() {
//
//            }
//        });
//    }
//
//    /**
//     * 文件上传完成后，发送更新事件
//     */
//    private void sendUpdateEvent(TransResponse transResponse, boolean isMedia) {
//        if (!isMedia) {
//            UpdateCoursewareStatueParam openMediaCoursewareParam = new UpdateCoursewareStatueParam(SocketType.Message.CLIENT_COURSEWARE_STATUS);
//            openMediaCoursewareParam.fileId = transResponse.fileInfo.id;
//            openMediaCoursewareParam.fileName = transResponse.fileInfo.name;
//            openMediaCoursewareParam.suffix = transResponse.fileInfo.ext;
//            openMediaCoursewareParam.isDelete = !SetPreferencesUtils.getRetainStatus();
//            OnlineClassSocketManager.getInstance().send(openMediaCoursewareParam);
//        } else {
//            UpdateCoursewareStatueParam openMediaCoursewareParam = new UpdateCoursewareStatueParam(SocketType.Message.CLIENT_OPEN_MULTIMEDIA);
//            openMediaCoursewareParam.fileId = transResponse.fileInfo.id;
//            openMediaCoursewareParam.fileName = transResponse.fileInfo.name;
//            openMediaCoursewareParam.suffix = transResponse.fileInfo.ext;
//            openMediaCoursewareParam.isDelete = !SetPreferencesUtils.getRetainStatus();
//            OnlineClassSocketManager.getInstance().send(openMediaCoursewareParam);
//        }
//    }
//
//    private void showAnimal(Bitmap bitmap) {
//        mActivity.showMediaCoursewareAnimal(bitmap);
//    }
//
//    public boolean isShowRecordVideoDialog() {
//        return isShowRecordVideoDialog;
//    }
//
//    public void setShowRecordVideoDialog(boolean showRecordVideoDialog) {
//        isShowRecordVideoDialog = showRecordVideoDialog;
//    }
//
//    public RecordVideoDialogActivity getmRecordVideoDialog() {
//        return mRecordVideoDialog;
//    }
//
//    /**
//     * 关闭正在使用的音频录制
//     *
//     * @return 是否关闭过音频
//     */
//    public boolean closeUseingMediaRecord() {
//        if (mActivity.getIsLocalOpenAudio()) {
//            mActivity.switchMic();
//            mHasClosedMediaRecord = true;
//            return true;
//        }
//        return false;
//    }
//
//    public void resumeClosedMediaRedor() {
//        if (mHasClosedMediaRecord) {
//            mActivity.switchMic();
//            mHasClosedMediaRecord = false;
//        }
//    }
//
//    public void setTempImagePath(String tempImagePath) {
//        this.mTempImagePath = tempImagePath;
//    }
//
//    public void showPhotoDialog() {
//        File imageFile = new File(mTempImagePath);
//        if (!imageFile.exists()) return;
//        if (mPhotoDialog != null && mPhotoDialog.isShowing()) {
//            mPhotoDialog.addPhoto(imageFile);
//        } else {
//            mPhotoDialog = new RecordPhotoDialog(mActivity, imageFile, new RecordPhotoDialog.OnRecordPhotoDialogClickListener() {
//                @Override
//                public void onPhotoDialogClick(int flag) {
//                    switch (flag) {
//                        case RecordPhotoDialog.RERECORD_FLAG:        //重拍
//                            openCamera();
//                            break;
//                        case RecordPhotoDialog.NEWRECORD_FLAG:
//                            // 新加
//                            openCamera();
//                            break;
//                        default:
//                            break;
//                    }
//                }
//
//                @Override
//                public void onPhotoSubmit(HashMap<Long, Bitmap> bitmaps) {
//                    dealwithPhoto(bitmaps);
//                }
//            });
//        }
//        mPhotoDialog.show();
//    }
//
//    public void openCamera() {
//        String tempImagePath = FileUtils.getTempImagePath();
//        File file = new File(tempImagePath);
//        setTempImagePath(tempImagePath);
//        Uri u = FileProvider.getUriForFile(mActivity, file);
//        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        intent.putExtra(MediaStore.Images.Media.ORIENTATION, 0);
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, u);
//        mActivity.startActivityForResult(intent, 1001);
//    }
//}
