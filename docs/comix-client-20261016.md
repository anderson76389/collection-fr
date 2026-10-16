# Comix 1.6.54 — 16 October 2026

The previous 1.6.53 retry changes did not resolve the user's HTTP 502 errors.
This update adapts the newer, still-open upstream proposals
[20048](https://github.com/keiyoushi/extensions-source/pull/20048) (head
`c9ba66a6807da190afe5d850bfdda85daaa0b473`) and its input dependency
[20047](https://github.com/keiyoushi/extensions-source/pull/20047).

## Changes

- Read the decoded result from the site's own GET helper instead of capturing
  `Object.prototype.items`. Use it for both chapter pagination and reader pages.
- Remove the forced `X-Rt: 0` override. Dispatch native touchscreen swipes before
  chapter-list requests, as in the upstream proposal.
- Keep native input and WebView lifecycle code local to Comix in `InputWebView.kt`;
  do not import the upstream core update or alter shared libraries. It adapts the
  existing core runner and the swipe implementation from upstream 20047, using
  the legacy asynchronous-posted bridge supported by this fork's runtime.
- Preserve package, source ID, language `all`, APK naming, signing configuration,
  preferences, request pacing, title cache, complete-list cache, image retries,
  descrambling and alternate image hosts.
- Incomplete or blocked pagination must remain incomplete, preserving any
  already fetched chapters and reporting a real error when nothing was read.

## Verification and limits

`node --experimental-vm-modules --test src/all/comix/tests/client.test.mjs`:
8 passing tests covering multi-page retrieval, known chapter stopping, partial
and first-page 502 failures, incomplete lists, image metadata/alternate hosts,
bare image arrays, reader HTTP errors and discovery of the site's GET helper.
These tests use fixtures, not live site responses. Release assembly and Android
lint are checked by the pull request's GitHub Actions workflow before merging.

No Android device running Mihon is available in this environment. The native
touch path, actual site acceptance and end-to-end reading therefore still need
confirmation in Mihon; compilation and fixture tests do not prove those work.
A 502 on the initial HTML navigation can still occur before this API correction
runs. The upstream proposal is open, not an upstream-merged guarantee.

## Soft Epsilon remains unresolved

The user confirms that 1.6.8 still reports an incompatible reader. Inspection of
the current reader bundles (`CSKhBvn8b.js` and `CSL6ERowb.js`) finds a server-backed
attestation step returning `supported: false`, which the site's reader turns
into the incompatible-device message. It includes graphics and device checks;
the exact rejected criterion on the user's phone is not established. A working
catalogue or chapter API is not evidence that the reader is working. No Epsilon
version bump or claim of a verified reader fix is included in this change.

MangaDex 1.6.8 and removal of Sushiscan were published separately in PR 46.
