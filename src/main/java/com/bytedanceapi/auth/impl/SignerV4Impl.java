package com.bytedanceapi.auth.impl;

import com.bytedanceapi.auth.ISignerV4;
import com.bytedanceapi.auth.MedaData;
import com.bytedanceapi.helper.Const;
import com.bytedanceapi.helper.Utils;
import com.bytedanceapi.model.Credentials;
import com.bytedanceapi.service.SignableRequest;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.EntityUtils;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class SignerV4Impl implements ISignerV4 {
    private static final TimeZone tz = TimeZone.getTimeZone("UTC");
    private static final Set<String> H_INCLUDE = new HashSet<String>();

    static {
        H_INCLUDE.add("Content-Type");
        H_INCLUDE.add("Content-Md5");
        H_INCLUDE.add("Host");
    }

    @Override
    public void sign(SignableRequest request, Credentials credentials) throws Exception {
        signV4(request, credentials);
        request.setURI(request.getUriBuilder().build());
    }

    @Override
    public String signUrl(SignableRequest request, Credentials credentials) throws Exception {
        String formatDate = getCurrentFormatDate();
        String date = formatDate.substring(0, 8);

        MedaData meta = new MedaData();
        meta.setDate(date);
        meta.setService(credentials.getService());
        meta.setRegion(credentials.getRegion());
        meta.setSignedHeaders("");
        meta.setAlgorithm("AWS4-HMAC-SHA256");
        meta.setCredentialScope(String.join("/", meta.getDate(), meta.getRegion(), meta.getService(), "aws4_request"));

        URIBuilder builder = request.getUriBuilder();
        builder.setParameter("X-Amz-Date", formatDate);
        builder.setParameter("X-Amz-NotSignBody", "");
        builder.setParameter("X-Amz-Credential", credentials.getAccessKeyID() + "/" + meta.getCredentialScope());
        builder.setParameter("X-Amz-Algorithm", meta.getAlgorithm());
        builder.setParameter("X-Amz-SignedHeaders", meta.getSignedHeaders());
        builder.setParameter("X-Amz-SignedQueries", "");
        List<String> keys = new ArrayList<>();
        for (NameValuePair pair : builder.getQueryParams()) {
            keys.add(pair.getName());
        }
        keys.sort(Comparator.naturalOrder());

        builder.setParameter("X-Amz-SignedQueries", String.join(";", keys));

        // step 1
        String hashedCanonReq = hashedSimpleCanonicalRequestV4(request, meta);

        // step 2
        String stringToSign = String.join("\n", meta.getAlgorithm(), formatDate, meta.getCredentialScope(), hashedCanonReq);

        // step 3
        byte[] signingKey = genSigningSecretKeyV4(credentials.getSecretAccessKey(), meta.getDate(), meta.getRegion(), meta.getService());
        String signature = signatureV4(signingKey, stringToSign);

        builder.setParameter("X-Amz-Signature", signature);
        return builder.build().toURL().getQuery();
    }

    private void signV4(SignableRequest request, Credentials credentials) throws Exception {
        URIBuilder builder = request.getUriBuilder();
        if (builder.getPath().equals("")) {
            builder.setPath(builder.getPath() + "/");
        }

        // common headers
        request.setHeader("Host", request.getUriBuilder().getHost());
        if (request.getHeaders("Content-Type") == null) {
            request.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        }
        String formatDate = getCurrentFormatDate();
        request.setHeader("X-Amz-Date", formatDate);

        MedaData meta = new MedaData();
        meta.setAlgorithm("AWS4-HMAC-SHA256");
        meta.setService(credentials.getService());
        meta.setRegion(credentials.getRegion());
        meta.setDate(toDate(formatDate));

        // step 1
        String hashedCanonReq = hashedCanonicalRequestV4(request, meta);

        meta.setCredentialScope(String.join("/", meta.getDate(), meta.getRegion(), meta.getService(), "aws4_request"));
        // step 2
        String stringToSign = String.join("\n", meta.getAlgorithm(), formatDate, meta.getCredentialScope(), hashedCanonReq);

        // step 3
        byte[] signingKey = genSigningSecretKeyV4(credentials.getSecretAccessKey(), meta.getDate(), meta.getRegion(), meta.getService());
        String signature = Hex.encodeHexString(Utils.hmacSHA256(signingKey, stringToSign));
        request.setHeader("Authorization", buildAuthHeaderV4(signature, meta, credentials));
    }

    private String hashedSimpleCanonicalRequestV4(SignableRequest request, MedaData meta) throws Exception {
        String payloadHash = Utils.hashSHA256(new byte[0]);

        URIBuilder builder = request.getUriBuilder();
        if (builder.getPath().equals("")) {
            builder.setPath("/");
        }

        String canonicalRequest = String.join("\n", request.getMethod(), normUri(builder.getPath()),
                normQuery(builder.getQueryParams()), "\n", meta.getSignedHeaders(), payloadHash);

        return Utils.hashSHA256(canonicalRequest.getBytes());
    }

    private String hashedCanonicalRequestV4(SignableRequest request, MedaData meta) throws Exception {
        byte[] body;
        HttpEntity entity = request.getEntity();
        if (entity == null) {
            body = new byte[0];
        } else {
            body = EntityUtils.toByteArray(entity);
        }
        String bodyHash = Utils.hashSHA256(body);
        request.setHeader("X-Amz-Content-Sha256", bodyHash);

        List<String> signedHeaders = new ArrayList<>();
        for (Header header : request.getAllHeaders()) {
            String headerName = header.getName();
            if (H_INCLUDE.contains(headerName) || headerName.startsWith("X-Amz-")) {
                signedHeaders.add(headerName.toLowerCase());
            }
        }
        signedHeaders.sort(Comparator.naturalOrder());
        StringBuilder signedHeadersToSignStr = new StringBuilder();
        for (String h : signedHeaders) {
            String value = request.getFirstHeader(h).getValue().trim();
            if (h.equals("host")) {
                if (value.contains(":")) {
                    String[] split = value.split(":");
                    String port = split[1];
                    if (port.equals("80") || port.equals("443")) {
                        value = split[0];
                    }
                }
            }
            signedHeadersToSignStr.append(h).append(":").append(value).append("\n");
        }

        meta.setSignedHeaders(String.join(";", signedHeaders));

        String canonicalRequest = String.join("\n", request.getMethod(), normUri(request.getUriBuilder().getPath()),
                normQuery(request.getUriBuilder().getQueryParams()), signedHeadersToSignStr.toString(),
                meta.getSignedHeaders(), bodyHash);

        return Utils.hashSHA256(canonicalRequest.getBytes());
    }

    private String signatureV4(byte[] signingKey, String stringToSign) throws Exception {
        return Hex.encodeHexString(Utils.hmacSHA256(signingKey, stringToSign));
    }

    private byte[] genSigningSecretKeyV4(String secretKey, String date, String region, String service) throws Exception {
        byte[] kDate = Utils.hmacSHA256(("AWS4" + secretKey).getBytes(), date);
        byte[] kRegion = Utils.hmacSHA256(kDate, region);
        byte[] kService = Utils.hmacSHA256(kRegion, service);
        return Utils.hmacSHA256(kService, "aws4_request");
    }

    private String buildAuthHeaderV4(String signature, MedaData meta, Credentials credentials) {
        String credential = credentials.getAccessKeyID() + "/" + meta.getCredentialScope();

        return meta.getAlgorithm() +
                " Credential=" + credential +
                ", SignedHeaders=" + meta.getSignedHeaders() +
                ", Signature=" + signature;
    }

    private String getCurrentFormatDate() {
        DateFormat df = new SimpleDateFormat(Const.TIME_FORMAT_V4);
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    private String toDate(String timestamp) {
        return timestamp.substring(0, 8);
    }

    private String normUri(String path) {
        return URLEncoder.encode(path).replace("%2F", "/").replace("+", "%20");
    }

    private String normQuery(List<NameValuePair> params) {
        params.sort(Comparator.comparing(NameValuePair::getName));
        String query = URLEncodedUtils.format(params, Consts.UTF_8);
        return query.replace("+", "%20");
    }

}
