// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
import 'ts-polyfill/lib/es2016-array-include';
// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';

// Our scripts
import "./admin";
import "./application";
import "./applicationMandatFields";
import "./application-attachment";
import "./autolinker";
import "./changeArea";
import "./domHelpers";
import "./editGroup";
import "./editMyGroups";
import "./editUser";
import "./formExitAlert";
import "./mdl-extensions";
import "./notificationBanner";
import "./searchInput";
import "./showApplication";
import "./signup";
import "./slimselect";
import "./validateAccount";
import "./welcome";
