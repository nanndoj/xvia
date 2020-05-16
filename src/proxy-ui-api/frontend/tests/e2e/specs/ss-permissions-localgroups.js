
module.exports = {
  tags: ['ss', 'localgroups', 'permissions'],
  'Local groups system administrator role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const keysTab = mainPage.section.keysTab;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_system_administrator)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_system_administrator+'")]');

    // System admin should be in keys and certs view and not see clients tab
    browser.waitForElementVisible(keysTab)
    browser.waitForElementNotPresent(clientsTab)

    browser.end();
  },
  'Local groups security officer role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_security_officer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_security_officer+'")]');

    // Security officer should see clients list
    mainPage.openClientsTab();

    // Security officer should not see clients details and thus not local groups
    clientsTab.openTestGov();
    browser.waitForElementNotPresent(clientInfo);

    browser.end();
  },
  'Local groups registration officer role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_registration_officer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_registration_officer+'")]');

    // Registration officer should not see local groups in clients details
    mainPage.openClientsTab();
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);
    browser.waitForElementNotPresent(clientInfo.elements.localGroupsTab)

    browser.end();
  },
  'Local groups service administrator role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;
    const localGroupPopup = mainPage.section.localGroupPopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_service_administrator)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_service_administrator+'")]');

    // Service administrator should see local groups list
    mainPage.openClientsTab();
    clientsTab.openTestService();
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Service administrator should see add local groups button
    browser.waitForElementVisible(clientLocalGroups.elements.addGroupButton);

    //  Service administrator should see local groups members and edit buttons
    clientLocalGroups.openAbbDetails();
    browser.waitForElementVisible('//span[contains(@class, "cert-headline") and contains(text(), "abb")]');

    browser.waitForElementVisible(localGroupPopup.elements.localGroupAddMembersButton);
    browser.waitForElementVisible(localGroupPopup.elements.localGroupRemoveAllButton);
    browser.waitForElementVisible(localGroupPopup.elements.localGroupTestComRemoveButton);
    browser.waitForElementVisible(localGroupPopup.elements.localGroupDeleteButton);

    browser.end();
  },
  'Local groups security server observer role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;
    const localGroupPopup = mainPage.section.localGroupPopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_securityserver_observer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_securityserver_observer+'")]');

    // Security server observer should see local groups list
    mainPage.openClientsTab();
    clientsTab.openTestService();
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Security server observer should not see add local groups button
    browser.waitForElementNotPresent(clientLocalGroups.elements.addGroupButton);
 
    // security server observer should see local group members but not be able to edit them
    clientLocalGroups.openAbbDetails();

    browser.waitForElementVisible('//span[contains(@class, "cert-headline") and contains(text(), "abb")]');
    browser.waitForElementVisible('//tr[.//*[contains(text(), "TestCom")]]');
    browser.waitForElementNotPresent(localGroupPopup.elements.localGroupAddMembersButton);
    browser.waitForElementNotPresent(localGroupPopup.elements.localGroupRemoveAllButton);
    browser.waitForElementNotPresent(localGroupPopup.elements.localGroupTestComRemoveButton);
    browser.waitForElementNotPresent(localGroupPopup.elements.localGroupDeleteButton);

    browser.end();
  }
};
