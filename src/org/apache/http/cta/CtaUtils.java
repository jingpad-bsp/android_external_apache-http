package org.apache.http.cta;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.util.EncodingUtils;

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public final class CtaUtils {
    private static final String MMS_PERMISSION = "com.unisoc.permission.CTA_SEND_MMS";

    public static boolean isCtaGrantSending(HttpRequest request, HttpParams defaultParams) {
        try {
            Method method = null;
            Class<?> cls = Class.forName("android.cta.PermissionUtils");
            if (method == null) {
                method = cls.getMethod("isCtaFeatureSupported");
            }

            if (!(Boolean) method.invoke(null)) {
                return true;
            }

            if (isSendMMS(request, defaultParams)) {
                method = cls.getMethod("checkCtaPermission", String.class);
                return (Boolean) method.invoke(null, MMS_PERMISSION);
            }
        } catch (ReflectiveOperationException e) {
            if (e.getCause() instanceof SecurityException) {
                throw new SecurityException(e.getCause());
            }
        }catch(Throwable ee){
            if (ee instanceof NoClassDefFoundError) {
                System.out.println("[CtaSecurity] ee:" + ee);
            }
        }
        return true;
    }

    private static boolean isSendMMS(HttpRequest request, HttpParams defaultParams) {
        final String mmsType = "application/vnd.wap.mms-message";
        RequestLine reqLine = request.getRequestLine();
        if (reqLine.getMethod().equals(HttpPost.METHOD_NAME)) {
            String userAgent = HttpProtocolParams.getUserAgent(defaultParams);
            if (userAgent != null && userAgent.indexOf("MMS") != -1) {
                return isPduSendMMS(request);
            } else {
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    if (entity != null) {
                        Header httpHeader = entity.getContentType();
                        if (httpHeader != null && httpHeader.getValue() != null) {
                            if (httpHeader.getValue().startsWith(mmsType)) {
                                return isPduSendMMS(request);
                            }
                        }
                    }
                    Header[] headers = request.getHeaders(HTTP.CONTENT_TYPE);
                    if (headers != null) {
                        for (Header header : headers) {
                            if (header.getValue().indexOf(mmsType) != -1) {
                                return isPduSendMMS(request);
                            }
                        }
                    }

                    headers = request.getHeaders("ACCEPT");
                    if (headers != null) {
                        for (Header header : headers) {
                            if (header.getValue().indexOf(mmsType) != -1) {
                                return isPduSendMMS(request);
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPduSendMMS(HttpRequest request) {

        if (request instanceof HttpEntityEnclosingRequest) {
            System.out.println("[CtaSecurity]: Check isPduSendMMS");
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity != null) {
                InputStream nis = null;
                byte[] buf = new byte[2];
                int len = 0;

                try {
                    InputStream is = entity.getContent();
                    Header contentEncoding = entity.getContentEncoding();
                    if (contentEncoding != null
                            && ("gzip").equals(contentEncoding.getValue())) {
                        nis = new GZIPInputStream(is);
                    } else {
                        nis = is;
                    }

                    len = nis.read(buf);
                    System.out.println("[CtaSecurity]: PDU read len:" + len);
                    if (len == 2) {
                        //Convert to unsigned byte
                        System.out.println("CtaSecurity PDU Type:"
                                + (buf[0] & 0xFF) + ":" + (buf[1] & 0xFF));
                        //X-Mms-Message-Type: m-send-req (0x80)
                        if ((buf[0] & 0xFF) == 0x8C && (buf[1] & 0xFF) == 0x80) {
                            return true;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("[CtaSecurity]:" + e);
                } catch (IndexOutOfBoundsException ee) {
                    System.out.println("[CtaSecurity]:" + ee);
                }
            }
        }
        return false;
    }

    public static HttpResponse deniedHttpResponse() {
        StatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1,
                HttpStatus.SC_BAD_REQUEST, "Bad Request");
        HttpResponse response = new BasicHttpResponse(statusLine);

        byte[] msg = EncodingUtils.getAsciiBytes("User Permission is denied");
        ByteArrayEntity entity = new ByteArrayEntity(msg);
        entity.setContentType("text/plain; charset=US-ASCII");
        response.setEntity(entity);

        return response;
    }

}