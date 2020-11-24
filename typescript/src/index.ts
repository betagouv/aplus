// Polyfills
// See https://github.com/ryanelian/ts-polyfill/tree/master/lib
import 'ts-polyfill/lib/es2015-core';
import 'ts-polyfill/lib/es2016-array-include';
// This adds NodeList.forEach, etc.
import 'core-js/web/dom-collections';
// This adds dialog polyfill
import dialogPolyfill from "dialog-polyfill";

// Our scripts
import "./application"
import "./admin.ts"
import "./mdl-extensions.ts"
import "./validateAccount"
import "./application-attachment"
import "./anchorme"
import "./showApplication"