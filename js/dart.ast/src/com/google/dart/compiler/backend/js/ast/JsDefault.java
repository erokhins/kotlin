// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.backend.js.ast;

/**
 * Represents the default option in a JavaScript swtich statement.
 */
public final class JsDefault extends JsSwitchMember {
    @Override
    public void accept(JsVisitor v) {
        v.visitDefault(this);
    }
}
