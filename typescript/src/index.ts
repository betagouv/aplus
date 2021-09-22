// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
// new Promise
import 'ts-polyfill/lib/es2015-promise';
import 'ts-polyfill/lib/es2016-array-include';
// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';
// fetch
import 'unfetch/polyfill';
// String.prototype.normalize
import 'unorm';

// Our scripts
import "./admin";
import "./application";
import "./applicationMandatFields";
import "./applicationAttachment";
//import "./autolinker";
import "./changeArea";
import "./domHelpers";
import "./editGroup";
import "./editMyGroups";
import "./editUser";
import "./formExitAlert";
import "./magicLinkAntiConsumption";
import "./mdl-extensions";
import "./myApplications";
import "./notificationBanner";
import "./searchInput";
import "./showApplication";
import "./signup";
import "./slimselect";
import "./users";
import "./validateAccount";
import "./welcome";
