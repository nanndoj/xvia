/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.openapi;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;

/**
 * api util
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class ApiUtil {

    /**
     * Create a specified example response, to return from endpoints not implemented yet
     * @param req
     * @param contentType
     * @param example
     */
    public static void setExampleResponse(NativeWebRequest req, String contentType, String example) {
        try {
            HttpServletResponse res = req.getNativeResponse(HttpServletResponse.class);
            res.setCharacterEncoding("UTF-8");
            res.addHeader("Content-Type", contentType);
            res.getWriter().print(example);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a ResponseEntity with status code 201 CREATED
     * with response body <code>body</code> and <code>Location</code>
     * header that points to <code>path</code>, which has been expanded with
     * <code>uriVariableValues</code>
     * @param path
     * @param body
     * @param uriVariableValues
     * @param <T>
     * @return
     */
    public static <T> ResponseEntity<T> createCreatedResponse(String path,
            T body, Object... uriVariableValues) {
        URI location = ServletUriComponentsBuilder
                               .fromCurrentRequest()
                               .replacePath(path)
                               .buildAndExpand(uriVariableValues)
                               .toUri();
        return ResponseEntity.created(location)
                       .body(body);
    }

    /**
     * Creates a ResponseEntity that contains an attachment with given filename
     * @param attachmentBytes
     * @param filename
     * @return
     */
    public static ResponseEntity<Resource> createAttachmentResourceResponse(byte[] attachmentBytes, String filename) {
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(filename)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);

        Resource resource = new ByteArrayResource(attachmentBytes);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }
}
