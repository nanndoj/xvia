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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.InitializationStatusDto;
import org.niis.xroad.restapi.openapi.model.InitialServerConf;
import org.niis.xroad.restapi.openapi.model.InitializationStatus;
import org.niis.xroad.restapi.openapi.validator.InitialServerConfValidator;
import org.niis.xroad.restapi.service.AnchorNotFoundException;
import org.niis.xroad.restapi.service.InitializationService;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Init (Security Server) controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class InitializationApiController implements InitializationApi {
    private final InitializationService initializationService;

    @Autowired
    public InitializationApiController(InitializationService initializationService) {
        this.initializationService = initializationService;
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public ResponseEntity<InitializationStatus> getInitializationStatus() {
        InitializationStatusDto initializationStatusDto =
                initializationService.getSecurityServerInitializationStatus();
        InitializationStatus initializationStatus = new InitializationStatus();
        initializationStatus.setIsAnchorImported(initializationStatusDto.isAnchorImported());
        initializationStatus.setIsServerCodeInitialized(initializationStatusDto.isServerCodeInitialized());
        initializationStatus.setIsServerOwnerInitialized(initializationStatusDto.isServerOwnerInitialized());
        initializationStatus.setIsSoftwareTokenInitialized(initializationStatusDto.isSoftwareTokenInitialized());
        return new ResponseEntity<>(initializationStatus, HttpStatus.OK);
    }

    @InitBinder("initialServerConf")
    @PreAuthorize("permitAll()")
    protected void initInitialServerConfBinder(WebDataBinder binder) {
        binder.addValidators(new InitialServerConfValidator());
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public synchronized ResponseEntity<Void> initSecurityServer(InitialServerConf initialServerConf) {
        String securityServerCode = initialServerConf.getSecurityServerCode();
        String ownerMemberClass = initialServerConf.getOwnerMemberClass();
        String ownerMemberCode = initialServerConf.getOwnerMemberCode();
        String softwareTokenPin = initialServerConf.getSoftwareTokenPin();
        boolean ignoreWarnings = Boolean.TRUE.equals(initialServerConf.getIgnoreWarnings());
        try {
            initializationService.initialize(securityServerCode, ownerMemberClass, ownerMemberCode, softwareTokenPin,
                    ignoreWarnings);
        } catch (AnchorNotFoundException e) {
            throw new ConflictException(e);
        } catch (UnhandledWarningsException | InitializationService.InvalidCharactersException
                | InitializationService.WeakPinException | InitializationService.MissingInitParamsException e) {
            throw new BadRequestException(e);
        } catch (InitializationService.SoftwareTokenInitException e) {
            throw new InternalServerErrorException(e);
        }
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
