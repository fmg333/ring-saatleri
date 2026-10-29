package tr.ring.saatleri;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * İndirilen APK'yı sistemin kurulum ekranına açabilmek için gereken en küçük sağlayıcı.
 * Uygulamanın kendi klasöründeki tek bir dosyayı sunar, dışarıya kapalıdır.
 */
public class ApkProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = Updater.apkFile(getContext());
        if (!f.exists()) {
            throw new FileNotFoundException(f.getPath());
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    /** Kurulum ekranı dosyanın adını ve boyutunu sorar. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f = Updater.apkFile(getContext());
        MatrixCursor cur = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, 1);
        cur.addRow(new Object[]{f.getName(), Long.valueOf(f.length())});
        return cur;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
