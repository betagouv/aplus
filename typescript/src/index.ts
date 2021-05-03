// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
import 'ts-polyfill/lib/es2016-array-include';
// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';

// Our scripts
import "./application"
import "./admin"
import "./mdl-extensions"
import "./validateAccount"
import "./application-attachment"
import "./anchorme"
import "./showApplication"
import "./signup"
import "./editMyGroups"
import "./editUser"
import "./editGroup"
