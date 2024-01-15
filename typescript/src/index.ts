// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
// new Promise
import 'ts-polyfill/lib/es2015-promise';
import 'ts-polyfill/lib/es2016-array-include';
// Needed by tabulator:
// Object.values() https://github.com/zloirock/core-js#ecmascript-object
import 'ts-polyfill/lib/es2017-object';

// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';
// URLSearchParams
import 'core-js/stable/url';
import 'core-js/stable/url-search-params';
// fetch
import 'unfetch/polyfill';
// String.prototype.normalize
import 'unorm';
// Proxy (needed by tabulator)
import 'proxy-polyfill';

import "./dsfr-fix.css"
import 'material-icons/iconfont/material-icons.css'

// Our scripts
import "./admin";
import "./applicationMandatFields";
import "./applicationAttachment";
import "./applicationsAdmin";
import "./changeArea";
import "./domHelpers";
import "./editGroup";
import "./editMyGroups";
import "./editUser";
import "./formExitAlert";
import "./franceServices";
import "./magicLinkAntiConsumption";
import "./mandat";
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
