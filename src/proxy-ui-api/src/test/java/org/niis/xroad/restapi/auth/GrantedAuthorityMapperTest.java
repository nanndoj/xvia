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
package org.niis.xroad.restapi.auth;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Test GrantedAuthorityMapper
 */
public class GrantedAuthorityMapperTest {

    private GrantedAuthorityMapper mapper;

    @Before
    public void setup() {
        mapper = new GrantedAuthorityMapper();
    }

    @Test
    public void simpleMapping() {
        String roleName = "XROAD_REGISTRATION_OFFICER";
        Set<GrantedAuthority> authorities = mapper.getAuthorities(
                Collections.singletonList(Role.valueOf(roleName)));
        assertTrue(authorities.size() > 1);
        Set<String> authStrings = authorities.stream().map(
                auth -> auth.getAuthority())
                .collect(Collectors.toSet());
        assertTrue(authStrings.contains("ROLE_" + roleName));
        assertTrue(authStrings.contains("ADD_CLIENT"));
        assertFalse(authStrings.contains("INIT_CONFIG"));
    }

    @Test
    public void simpleMappingSystemAdmin() {
        String roleName = "XROAD_SYSTEM_ADMINISTRATOR";
        Set<GrantedAuthority> authorities = mapper.getAuthorities(
                Collections.singletonList(Role.valueOf(roleName)));
        Set<String> authStrings = authorities.stream().map(
                auth -> auth.getAuthority())
                .collect(Collectors.toSet());
        assertTrue(authStrings.contains("INIT_CONFIG"));
    }

}
