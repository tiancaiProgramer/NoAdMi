import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

// Exercise the exact script shipped in SystemAdHooks without Android dependencies.
const source = fs.readFileSync(new URL('../../src/main/java/io/github/miclean/module/SystemAdHooks.java', import.meta.url), 'utf8');
const part = source.split('String script = ')[1].split(';\n                        web.evaluateJavascript')[0];
const code = [...part.matchAll(/"(?:\\.|[^"\\])*"/g)].map(x => JSON.parse(x[0])).join('');
let checks = 0;
for (let bits = 0; bits < 8; bits++) {
    const siblings = {'.get-width': bits & 1, '.mask': bits & 2};
    const parent = {style: {}, querySelector: s => siblings[s] || null};
    const bg = {parentElement: parent, querySelector: () => bits & 4 ? {} : null};
    let callback, subscriptions = 0;
    const context = {
        window: {}, document: {documentElement: {}, querySelectorAll: () => [bg]},
        MutationObserver: class {
            constructor(fn) { callback = fn; }
            observe() { subscriptions++; }
        }
    };
    vm.runInNewContext(code, context);
    assert.equal(parent.style.display, bits === 7 ? 'none' : undefined); checks++;
    if (bits === 0) {
        siblings['.get-width'] = {}; siblings['.mask'] = {};
        bg.querySelector = () => ({}); callback();
        assert.equal(parent.style.display, 'none'); checks++;
    }
    vm.runInNewContext(code, context);
    assert.equal(subscriptions, 1); checks++;
}
console.log(`PASS: ${checks} empty promotion structure, asynchronous arrival and duplicate-observer checks`);
