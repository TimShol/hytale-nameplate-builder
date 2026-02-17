package com.frotty27.nameplatebuilder.server;

record UiState(ActiveTab activeTab, String filter, int availPage, int chainPage,
               int adminLeftPage, int adminRightPage, AdminSubTab adminSubTab,
               int adminDisLeftPage, int adminDisRightPage, int disabledPage) {
}
