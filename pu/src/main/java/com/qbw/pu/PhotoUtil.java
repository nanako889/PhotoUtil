package com.qbw.pu;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import com.qbw.log.XLog;

import java.io.File;

/**
 * 提供一个统一的接口，用于从相机或者图库获取照片
 */
public class PhotoUtil {

    private int mRequestCodeForCamera;
    private int mRequestCodeForGallery;
    private int mRequestCodeForCrop;

    /**
     * 是否需要裁剪图片
     */
    private boolean mNeedCrop;
    private int mCropWidth;
    private int mCropHeight;


    /**
     * 从相机获取图片的时候,图片保存到这个路径下面
     */
    private String mPhotoCameraSavePath;

    /**
     * 裁剪之后的图片保存在此路径格式
     */
    private String mPhotoCropSavePathFormat;
    private String mPhotoCropSavePath;

    /**
     * 保存请求值，用于删除相机产生的临时文件
     */
    private int mRequestFrom;

    private String mProviderNameAppend = "provider_photoutil";

    private Activity mActivity;
    private CallBack mCallBack;

    public PhotoUtil(Activity activity,
                     CallBack callBack,
                     int requestCodeForCamera,
                     int requestCodeForGallery,
                     int requestCodeForCrop) {
        mActivity = activity;
        mCallBack = callBack;
        mRequestCodeForCamera = requestCodeForCamera;
        mRequestCodeForGallery = requestCodeForGallery;
        mRequestCodeForCrop = requestCodeForCrop;
    }

    public void setProviderNameAppend(String providerNameAppend) {
        mProviderNameAppend = providerNameAppend;
    }

    /**
     * 从相机获取图片
     */
    public void getPhotoFromCamera(String photoCameraSavePath,
                                   int cropWidth,
                                   int cropHeight,
                                   String photoCropSavePathFormat) {
        mPhotoCameraSavePath = photoCameraSavePath;
        mNeedCrop = 0 != cropWidth && 0 != cropHeight;
        mCropWidth = cropWidth;
        mCropHeight = cropHeight;
        mPhotoCropSavePathFormat = photoCropSavePathFormat;
        XLog.d("photoCameraSavePath[%s], mNeedCrop[%b], cropWidth[%d], cropHeight[%d]",
               photoCameraSavePath,
               mNeedCrop,
               cropWidth,
               cropHeight);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,
                        getContentImageUri(mActivity, mPhotoCameraSavePath));
        mActivity.startActivityForResult(intent, mRequestCodeForCamera);
    }

    private Uri getContentImageUri(Activity activity, String filePath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(activity,
                                              activity.getPackageName() + "." + mProviderNameAppend,
                                              new File(filePath));
        } else {
            return Uri.fromFile(new File(filePath));
        }
    }

    public void getPhotoFromCamera(String photoCameraSavePath) {
        getPhotoFromCamera(photoCameraSavePath, 0, 0, "");
    }

    public void getPhotoFromCamera(int cropWidth, int cropHeight) {
        String headerPath = getFileDir(mActivity) + File.separator + "camera.jpg";
        String cropPath = getFileDir(mActivity) + File.separator + "crop.%s";
        getPhotoFromCamera(headerPath, cropWidth, cropHeight, cropPath);
    }

    public void getPhotoFromCamera() {
        getPhotoFromCamera(0, 0);
    }

    /**
     * 从图库获取图片
     */
    public void getPhotoFromGallery(int cropWidth, int cropHeight, String photoCropSavePathFormat) {
        mNeedCrop = 0 != cropWidth && 0 != cropHeight;
        mCropWidth = cropWidth;
        mCropHeight = cropHeight;
        mPhotoCropSavePathFormat = photoCropSavePathFormat;
        XLog.d("mNeedCrop[%b], cropWidth[%d], cropHeight[%d]", mNeedCrop, cropWidth, cropHeight);
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        mActivity.startActivityForResult(intent, mRequestCodeForGallery);
    }

    public void getPhotoFromGallery() {
        getPhotoFromGallery(0, 0, "");
    }

    public void getPhotoFromGallery(int cropWidth, int cropHeight) {
        String cropPath = getFileDir(mActivity) + File.separator + "crop.%s";
        getPhotoFromGallery(cropWidth, cropHeight, cropPath);
    }

    public void cropPhoto(boolean camera, Uri uri, String photoFormat) {
        XLog.v("uri=%s, photoFormat=%s", uri.toString(), photoFormat);
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("scale", "true");
        intent.putExtra("aspectX", mCropWidth);
        intent.putExtra("aspectY", mCropHeight);
        intent.putExtra("outputX", mCropWidth);
        intent.putExtra("outputY", mCropHeight);
        intent.putExtra("outputFormat", photoFormat);
        intent.putExtra("return-data", false);
        mPhotoCropSavePath = String.format(mPhotoCropSavePathFormat, photoFormat);
        XLog.d("crop save path[%s]", mPhotoCropSavePath);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mPhotoCropSavePath)));
        //intent.putExtra(MediaStore.EXTRA_OUTPUT, getContentImageUri(activity, mPhotoCropSavePath));
        intent.putExtra("noFaceDetection", true);
        if (camera) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        mActivity.startActivityForResult(intent, mRequestCodeForCrop);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == mRequestCodeForCamera || requestCode == mRequestCodeForGallery || requestCode == mRequestCodeForCrop) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    onActivityResultOk(requestCode, intent);
                    break;
                case Activity.RESULT_CANCELED:
                    mCallBack.onPhotoCancel();
                    break;
                case Activity.RESULT_FIRST_USER:
                    XLog.w("Start of user-defined activity results");
                    break;
                default:
                    mCallBack.onPhotoFailed();
                    break;
            }
        } else {
            XLog.w("Invalid requestCode[%d] for PhotoUtil", requestCode);
        }
    }

    private void onActivityResultOk(int requestCode, Intent intent) {
        if (mRequestCodeForCamera == requestCode) {
            mRequestFrom = requestCode;
            if (mNeedCrop) {
                cropPhoto(true, getContentImageUri(mActivity, mPhotoCameraSavePath), "jpg");
            } else {
                mCallBack.onPhotoCamera(mPhotoCameraSavePath);
            }
        } else if (mRequestCodeForGallery == requestCode) {
            mRequestFrom = requestCode;
            if (null != intent && null != intent.getData()) {
                String imagePath = getImagePath(mActivity, intent.getData());
                XLog.d("pick image[%s] from gallery", imagePath);
                if (mNeedCrop) {
                    cropPhoto(false, intent.getData(), getFileExtensionFromPath(imagePath));
                } else {
                    mCallBack.onPhotoGallery(imagePath);
                }
            } else {
                mCallBack.onPhotoFailed();
            }
        } else if (mRequestCodeForCrop == requestCode) {
            XLog.d("crop save path %s", mPhotoCropSavePath);
            mCallBack.onPhotoCrop(mPhotoCropSavePath);
            if (mRequestFrom == mRequestCodeForCamera) {
                XLog.w("delete file[%s] after crop", mPhotoCameraSavePath);
                deleteFile(mPhotoCameraSavePath);
            }
        }
    }

    private String getFileExtensionFromPath(String path) {
        return path.substring(path.lastIndexOf(".") + 1);
    }

    private File getFileDir(Context context) {
        return isExternalStorageExist() ? context.getExternalFilesDir(null) : context.getFilesDir();
    }

    private File getExternalStorageDir(Context context) {
        return isExternalStorageExist() ? Environment.getExternalStorageDirectory() : context.getFilesDir();
    }

    private boolean isExternalStorageExist() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment
                .isExternalStorageRemovable();
    }

    private boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
            return true;
        }
        return false;
    }

    private void deleteFile(File file) {
        if (file == null) {
            if (XLog.isEnabled()) XLog.e("file is null");
            return;
        }
        if (file.exists()) {
            if (file.isFile()) {
                if (!file.delete()) {
                    if (XLog.isEnabled()) XLog.e("delete file %s failed", file.getAbsolutePath());
                }
            } else {
                File[] files = file.listFiles();
                int len = files == null ? 0 : files.length;
                for (int i = 0; i < len; i++) {
                    deleteFile(files[i]);
                }
            }
        } else {
            if (XLog.isEnabled()) XLog.w("file %s not exist", file.getAbsolutePath());
        }
    }

    /**
     * @param uri
     * @return uri图片对应的路径
     */
    private String getImagePath(Context context, Uri uri) {
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        if (Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri(context.getApplicationContext(),
                                                                           uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),
                                                 Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver()
                                .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
                XLog.e(e);
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public interface CallBack {
        void onPhotoCamera(String photoPath);

        void onPhotoGallery(String photoPath);

        void onPhotoCrop(String photoPath);

        void onPhotoCancel();

        void onPhotoFailed();
    }
}
