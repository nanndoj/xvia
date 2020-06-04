/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.Endpoint;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceClientType;
import org.niis.xroad.restapi.openapi.model.ServiceClients;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.AccessRightService.AccessRightNotFoundException.ERROR_ACCESSRIGHT_NOT_FOUND;
import static org.niis.xroad.restapi.service.ClientNotFoundException.ERROR_CLIENT_NOT_FOUND;
import static org.niis.xroad.restapi.service.ServiceNotFoundException.ERROR_SERVICE_NOT_FOUND;

/**
 * Test ServicesApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
public class ServicesApiControllerIntegrationTest {

    private static final String SS1_PREDICT_WINNING_LOTTERY_NUMBERS = "FI:GOV:M1:SS1:predictWinningLotteryNumbers.v1";
    private static final String FOO = "foo";
    public static final int SS1_GET_RANDOM_SERVICE_CLIENTS = 4;

    @Autowired
    private ServicesApiController servicesApiController;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private GlobalConfService globalConfService;

    @Before
    public void setup() {
        when(globalConfFacade.getGlobalGroupDescription(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            GlobalGroupId id = (GlobalGroupId) args[0];
            return TestUtils.NAME_FOR + id.getGroupCode();
        });

        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return TestUtils.NAME_FOR + identifier.toShortString().replace("/", ":");
        });
        when(globalConfFacade.getMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM2),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        TestUtils.SUBSYSTEM3),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M2,
                        null))
        ));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateServiceHttps() {
        Service service = servicesApiController.getService(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(false);
        service.setUrl(TestUtils.URL_HTTPS);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service);

        Service updatedService = servicesApiController.updateService(TestUtils.SS1_GET_RANDOM_V1,
                serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(false, updatedService.getSslAuth());
        assertEquals(TestUtils.URL_HTTPS, updatedService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateServiceHttp() {
        Service service = servicesApiController.getService(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(true); // value does not matter if http - will aways be set to null
        service.setUrl(TestUtils.URL_HTTP);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service);

        Service updatedService = servicesApiController.updateService(TestUtils.SS1_GET_RANDOM_V1,
                serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertNull(updatedService.getSslAuth());
        assertEquals(TestUtils.URL_HTTP, updatedService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateServiceAll() {
        Service service = servicesApiController.getService(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(false);
        service.setUrl(TestUtils.URL_HTTPS);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service).urlAll(true)
                .sslAuthAll(true).timeoutAll(true);

        Service updatedService = servicesApiController.updateService(TestUtils.SS1_GET_RANDOM_V1,
                serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(false, updatedService.getSslAuth());
        assertEquals(TestUtils.URL_HTTPS, updatedService.getUrl());

        Service otherServiceFromSameServiceDesc = servicesApiController.getService(
                TestUtils.SS1_CALCULATE_PRIME).getBody();

        assertEquals(10, otherServiceFromSameServiceDesc.getTimeout().intValue());
        assertEquals(false, otherServiceFromSameServiceDesc.getSslAuth());
        assertEquals(TestUtils.URL_HTTPS, otherServiceFromSameServiceDesc.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateServiceOnlyUrlAll() {
        Service service = servicesApiController.getService(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(true);
        service.setUrl(TestUtils.URL_HTTPS);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service).urlAll(true);

        Service updatedService = servicesApiController.updateService(TestUtils.SS1_GET_RANDOM_V1,
                serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(true, updatedService.getSslAuth());
        assertEquals(TestUtils.URL_HTTPS, updatedService.getUrl());

        Service otherServiceFromSameServiceDesc = servicesApiController.getService(
                TestUtils.SS1_CALCULATE_PRIME).getBody();

        assertEquals(60, otherServiceFromSameServiceDesc.getTimeout().intValue());
        assertEquals(false, otherServiceFromSameServiceDesc.getSslAuth());
        assertEquals(TestUtils.URL_HTTPS, otherServiceFromSameServiceDesc.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES" })
    public void getServiceClientNotFound() {
        try {
            servicesApiController.getService(TestUtils.SS0_GET_RANDOM_V1).getBody();
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_CLIENT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES" })
    public void getServiceNotFound() {
        try {
            servicesApiController.getService(SS1_PREDICT_WINNING_LOTTERY_NUMBERS).getBody();
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_SERVICE_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL" })
    public void getServiceAccessRights() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClient serviceClient = getServiceClientByType(serviceClients, TestUtils.GLOBALGROUP).get();
        assertEquals(TestUtils.NAME_FOR + TestUtils.DB_GLOBALGROUP_CODE,
                serviceClient.getName());
        assertEquals(TestUtils.DB_GLOBALGROUP_ID, serviceClient.getId());
        assertEquals(TestUtils.GLOBALGROUP, serviceClient.getServiceClientType().name());

        serviceClient = getServiceClientByType(serviceClients, TestUtils.LOCALGROUP).get();
        assertEquals(TestUtils.DB_LOCAL_GROUP_ID_1, serviceClient.getId());
        assertEquals(TestUtils.DB_LOCAL_GROUP_CODE, serviceClient.getLocalGroupCode());
        assertEquals(FOO, serviceClient.getName());
        assertEquals(TestUtils.LOCALGROUP, serviceClient.getServiceClientType().name());

        serviceClient = getServiceClientByType(serviceClients, TestUtils.SUBSYSTEM).get();
        assertEquals(TestUtils.NAME_FOR + TestUtils.CLIENT_ID_SS2,
                serviceClient.getName());
        assertEquals(TestUtils.CLIENT_ID_SS2, serviceClient.getId());
        assertEquals(TestUtils.SUBSYSTEM, serviceClient.getServiceClientType().name());

        serviceClients = servicesApiController.getServiceServiceClients(TestUtils.SS1_CALCULATE_PRIME).getBody();
        assertEquals(1, serviceClients.size());

        // different versions of a service should have the same access rights
        serviceClients = servicesApiController.getServiceServiceClients(TestUtils.SS1_GET_RANDOM_V2).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        try {
            servicesApiController.getServiceServiceClients(TestUtils.SS0_GET_RANDOM_V1);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_CLIENT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }

        try {
            servicesApiController.getServiceServiceClients(SS1_PREDICT_WINNING_LOTTERY_NUMBERS);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_SERVICE_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    private Optional<ServiceClient> getServiceClientByType(List<ServiceClient> serviceClients, String type) {
        return serviceClients
                .stream()
                .filter(serviceClient -> serviceClient.getServiceClientType().name().equals(type))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRights() {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);

        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientType.GLOBALGROUP));

        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();
        serviceClients = servicesApiController.getServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS - 1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteMultipleServiceAccessRights() {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);

        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientType.GLOBALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2).serviceClientType(
                        ServiceClientType.SUBSYSTEM));

        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();
        serviceClients = servicesApiController.getServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS - 2, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteMultipleSameServiceAccessRights() {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);

        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientType.GLOBALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientType.GLOBALGROUP));

        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();
        serviceClients = servicesApiController.getServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS - 1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRightsWrongTypeIgnored() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        // ServiceClient.id is only field that matters when passing parameters to controller,
        // ServiceClient.serviceClientType is ignored
        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2).serviceClientType(
                        ServiceClientType.GLOBALGROUP));
        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();

        serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS - 1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRightsWrongTypeLocalGroupIgnored() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        // ServiceClient.id is only field that matters when passing parameters to controller,
        // ServiceClient.serviceClientType is ignored
        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2).serviceClientType(
                        ServiceClientType.LOCALGROUP));
        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();

        serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS - 1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRightsWithRedundantSubjects() {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);

        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2).serviceClientType(
                        ServiceClientType.SUBSYSTEM))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS3).serviceClientType(
                        ServiceClientType.SUBSYSTEM))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS4).serviceClientType(
                        ServiceClientType.SUBSYSTEM));
        try {
            servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1,
                    deletedServiceClients).getBody();
        } catch (BadRequestException expected) {
            assertEquals(ERROR_ACCESSRIGHT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRightsLocalGroupsWithRedundantSubjects() {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);

        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_1).serviceClientType(
                        ServiceClientType.LOCALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS3).serviceClientType(
                        ServiceClientType.SUBSYSTEM))
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                        ServiceClientType.LOCALGROUP));
        try {
            servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1,
                    deletedServiceClients).getBody();
        } catch (BadRequestException expected) {
            assertEquals(ERROR_ACCESSRIGHT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRightsWrongLocalGroupId() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_CODE).serviceClientType(
                        ServiceClientType.LOCALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS3).serviceClientType(
                        ServiceClientType.SUBSYSTEM))
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                        ServiceClientType.LOCALGROUP));
        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void deleteServiceAccessRightsWrongLocalGroupType() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients deletedServiceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                        ServiceClientType.GLOBALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS3).serviceClientType(
                        ServiceClientType.SUBSYSTEM));
        servicesApiController.deleteServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, deletedServiceClients).getBody();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void addAccessRights() {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_CALCULATE_PRIME).getBody();
        int calculatePrimeClientsBefore = 1;
        assertEquals(calculatePrimeClientsBefore, serviceClients.size());

        ServiceClients clientsToAdd = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                        ServiceClientType.LOCALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientType.GLOBALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2).serviceClientType(
                        ServiceClientType.SUBSYSTEM));

        List<ServiceClient> updatedServiceClients = servicesApiController
                .addServiceServiceClients(TestUtils.SS1_CALCULATE_PRIME, clientsToAdd).getBody();

        assertEquals(calculatePrimeClientsBefore + 3, updatedServiceClients.size());
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void addDuplicateAccessRight() throws Exception {
        when(globalConfService.clientsExist(any())).thenReturn(true);
        when(globalConfService.globalGroupsExist(any())).thenReturn(true);
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        // add subsystem TestUtils.CLIENT_ID_SS2 as duplicate
        ServiceClients clientsToAdd = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                        ServiceClientType.LOCALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2).serviceClientType(
                        ServiceClientType.SUBSYSTEM));
        servicesApiController.addServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, clientsToAdd);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void addDuplicatePreExistingAccessRight() {
        // try adding duplicate local group that already exists
        ServiceClients existingLocalGroup = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_1).serviceClientType(
                        ServiceClientType.LOCALGROUP));
        servicesApiController.addServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, existingLocalGroup);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void addDuplicateIdenticalAccessrights() {
        // try adding two identical localgroups
        List<ServiceClient> itemsBefore =
                servicesApiController.getServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1).getBody();
        ServiceClient localGroup = new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                ServiceClientType.LOCALGROUP);
        ServiceClients duplicateLocalGroups = new ServiceClients().addItemsItem(localGroup).addItemsItem(localGroup);
        servicesApiController.addServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, duplicateLocalGroups);
        List<ServiceClient> itemsAfter =
                servicesApiController.getServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertTrue(itemsBefore.size() + 1 == itemsAfter.size());
        assertTrue(itemsAfter.stream()
                .filter(item -> item.getId().equals(TestUtils.DB_LOCAL_GROUP_ID_2))
                .collect(Collectors.toList()).size() == 1);
    }


    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL" })
    public void addBogusAccessRight() {
        when(globalConfService.clientsExist(any())).thenReturn(false);
        when(globalConfService.globalGroupsExist(any())).thenReturn(false);
        List<ServiceClient> serviceClients = servicesApiController.getServiceServiceClients(
                TestUtils.SS1_GET_RANDOM_V1).getBody();
        assertEquals(SS1_GET_RANDOM_SERVICE_CLIENTS, serviceClients.size());

        ServiceClients clientsToAdd = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_2).serviceClientType(
                        ServiceClientType.LOCALGROUP))
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS2 + "foo").serviceClientType(
                        ServiceClientType.SUBSYSTEM));

        servicesApiController.addServiceServiceClients(TestUtils.SS1_GET_RANDOM_V1, clientsToAdd);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "ADD_OPENAPI3_ENDPOINT" })
    public void addDuplicateEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setMethod(Endpoint.MethodEnum.GET);
        endpoint.setPath("/foo");
        endpoint.setServiceCode("openapi3-test");
        servicesApiController.addEndpoint(TestUtils.SS6_OPENAPI_TEST, endpoint);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "ADD_OPENAPI3_ENDPOINT" })
    public void addEndpointToWSDL() {
        Endpoint endpoint = new Endpoint();
        endpoint.setMethod(Endpoint.MethodEnum.GET);
        endpoint.setPath("/foo");
        endpoint.setServiceCode("add-endpoint-to-wsdl-test");
        servicesApiController.addEndpoint(TestUtils.SS1_GET_RANDOM_V1, endpoint);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities =  { "ADD_OPENAPI3_ENDPOINT" })
    public void addEndpointWithId() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId("thereshouldntbeid");
        endpoint.setMethod(Endpoint.MethodEnum.GET);
        endpoint.setPath("/foo2");
        endpoint.setServiceCode("openapi3-test");
        servicesApiController.addEndpoint(TestUtils.SS6_OPENAPI_TEST, endpoint);
    }

    @Test
    @WithMockUser(authorities = { "ADD_OPENAPI3_ENDPOINT", "VIEW_CLIENT_SERVICES" })
    public void addEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setMethod(Endpoint.MethodEnum.GET);
        endpoint.setPath("/foo2");
        endpoint.setServiceCode("openapi3-test");
        servicesApiController.addEndpoint(TestUtils.SS6_OPENAPI_TEST, endpoint);

        Service service = servicesApiController.getService(TestUtils.SS6_OPENAPI_TEST).getBody();
        assertTrue(service.getEndpoints().stream().anyMatch(ep -> ep.getPath().equals(endpoint.getPath())
                && ep.getMethod().equals(endpoint.getMethod())
                && ep.getServiceCode().equals(endpoint.getServiceCode())));
    }
}
