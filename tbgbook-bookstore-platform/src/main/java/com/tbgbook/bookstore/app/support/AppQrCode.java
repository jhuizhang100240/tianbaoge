package com.tbgbook.bookstore.app.support;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

/**
 * 会员码 / 取货码二维码（PNG data URI）。
 *
 * @author tbgbook
 */
public final class AppQrCode
{
    private AppQrCode()
    {
    }

    public static String dataUri(String content)
    {
        try
        {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        }
        catch (Exception e)
        {
            throw new IllegalStateException("generate qrcode failed", e);
        }
    }
}
