Java Cancel Tokens
==================

This library implements a `CancelToken`, a tool for making cleanup easier. It is licensed under the MIT license.

Installation
============

Currently the library is a single file, [`src/com/strilanc/async/CancelToken.java`](https://github.com/Strilanc/Java-Cancel-Tokens/blob/master/src/com/strilanc/async/CancelToken.java), so installation consists of copying that file into your project and changing the package if it annoys you. I will expose it as a proper maven (or other) package if there is interest.

Exposed API
===========

- CancelToken
  - *static cancelled()*: Returns an already-cancelled token.
  - *static immortal()*: Returns an already-immortal token.
  - *getState()*: Determines whether the cancel token is still-cancellable, cancelled, or immortal.
  - *whenCancelled(callback)*: Registers a callback to be run when the token is cancelled. The callback runs right away if the token is already cancelled.
  - *whenCancelledBefore(callback, otherToken)*: Registers a callback to be run when the receiving token is cancelled, unless the other token is cancelled first.
- CancelToken.Source
  - *(constructor)*: Creates a new source that controls a new token.
  - *getToken()*: Returns the token controlled by the source.
  - *cancel()*: Attempts to cancel the controlled token, returning false if it was not still cancellable.
