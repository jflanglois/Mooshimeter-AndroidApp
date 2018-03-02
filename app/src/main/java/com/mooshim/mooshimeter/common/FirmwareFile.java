package com.mooshim.mooshimeter.common;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import timber.log.Timber;

public class FirmwareFile {
    // Singleton resources for accessing the bundled firmware image
    private static final int FILE_BUFFER_SIZE = 0x40000;
    public static final int OAD_BLOCK_SIZE = 16;
    public static final int OAD_BUFFER_SIZE = 2 + OAD_BLOCK_SIZE;
    private final byte[] mFileBuffer = new byte[FILE_BUFFER_SIZE];
    private int header_crc0;
    private int header_crc1;
    private int header_user_version;
    private int header_len_words;
    private int mFirmwareVersion;

    public int getVersion() {
        return mFirmwareVersion;
    }

    public byte[] getFileBuffer() {
        return mFileBuffer;
    }

    public byte[] getFileBlock(short bnum) {
        final byte[] rval = new byte[OAD_BLOCK_SIZE];
        System.arraycopy(mFileBuffer, bnum * OAD_BLOCK_SIZE, rval, 0, OAD_BLOCK_SIZE);
        return rval;
    }

    private void parseBuffer() {
        ByteBuffer b = ByteBuffer.wrap(mFileBuffer);
        b.order(ByteOrder.LITTLE_ENDIAN);
        header_crc0 = b.getShort();
        header_crc1 = b.getShort();
        header_user_version = b.getShort();
        header_len_words = b.getShort();
        if (header_len_words < 0) { // Because java is stupid and doesn't have unsigned types
            header_len_words &= 0x0000FFFF;
        }
        mFirmwareVersion = b.getInt();
    }

    private void loadFile(String path) {
        InputStream stream;
        try {
            // Read the file raw into a buffer
            stream = Util.getRootContext().getAssets().open(path);
            stream.read(mFileBuffer, 0, mFileBuffer.length);
            stream.close();
            parseBuffer();
        } catch (IOException e) {
            // Handle exceptions here
            Timber.e("Failed to unpack the firmware asset");
        }
    }

    private void downloadFromURL(final String urlStr) {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            Timber.e(e.getLocalizedMessage());
            e.getStackTrace();
            return;
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            InputStream stream = conn.getInputStream();
            int bytes_read = 0;
            int rval;
            while (-1 != (rval = stream.read(mFileBuffer, bytes_read, mFileBuffer.length - bytes_read))) {
                bytes_read += rval;
            }
            stream.close();
            parseBuffer();
            if (header_len_words * 4 != bytes_read) {
                Timber.e("Downloaded a file of mysterious provenance");
            } else {
                Timber.d("Successfully downloaded FW file.");
            }
        } catch (IOException e) {
            Timber.e(e.getLocalizedMessage());
            e.getStackTrace();
        }
    }

    @NonNull
    public static FirmwareFile fromURL(@NonNull String url) {
        FirmwareFile rval = new FirmwareFile();
        rval.downloadFromURL(url);
        return rval;
    }

    @NonNull
    public static FirmwareFile fromPath(@NonNull String path) {
        FirmwareFile rval = new FirmwareFile();
        rval.loadFile(path);
        return rval;
    }
}
