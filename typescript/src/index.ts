// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';

// Our scripts
import "./admin.ts"
import "./mdl-extensions.ts"
import "./validateAccount"
import "./application-attachment"
import "./anchorme"
