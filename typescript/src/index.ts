// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
import 'ts-polyfill/lib/es2016-array-include';
// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';

// Our scripts
import "./admin";
import "./application";
import "./application-attachment";
import "./autolinker";
import "./mdl-extensions";
import "./validateAccount";
import "./showApplication";
import "./signup";
import "./editMyGroups";
import "./editUser";
import "./editGroup";
import "./slimselect";
