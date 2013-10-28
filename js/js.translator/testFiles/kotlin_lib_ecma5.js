
// TODO drop this:
(function () {
    'use strict';

    if (!Array.isArray) {
        Array.isArray = function (vArg) {
            return Object.prototype.toString.call(vArg) === "[object Array]";
        };
    }

    if (!Function.prototype.bind) {
        Function.prototype.bind = function (oThis) {
            if (typeof this !== "function") {
                // closest thing possible to the ECMAScript 5 internal IsCallable function
                throw new TypeError("Function.prototype.bind - what is trying to be bound is not callable");
            }

            var aArgs = Array.prototype.slice.call(arguments, 1),
                fToBind = this,
                fNOP = function () {
                },
                fBound = function () {
                    return fToBind.apply(this instanceof fNOP && oThis
                                             ? this
                                             : oThis,
                                         aArgs.concat(Array.prototype.slice.call(arguments)));
                };

            fNOP.prototype = this.prototype;
            fBound.prototype = new fNOP();

            return fBound;
        };
    }

    if (!Object.keys) {
        Object.keys = function (o) {
            var result = [];
            var i = 0;
            for (var p in o) {
                if (o.hasOwnProperty(p)) {
                    result[i++] = p;
                }
            }
            return result;
        };
    }

    if (!Object.create) {
        Object.create = function(proto) {
            function F() {}
            F.prototype = proto;
            return new F();
        }
    }

    // http://ejohn.org/blog/objectgetprototypeof/
    if ( typeof Object.getPrototypeOf !== "function" ) {
        if ( typeof "test".__proto__ === "object" ) {
            Object.getPrototypeOf = function(object){
                return object.__proto__;
            };
        } else {
            Object.getPrototypeOf = function(object){
                // May break if the constructor has been tampered with
                return object.constructor.prototype;
            };
        }
    }
})();

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

var Kotlin = {};

(function () {

    function toArray(obj) {
        var array;
        if (obj == null) {
            array = [];
        }
        else if(!Array.isArray(obj)) {
            array = [obj];
        }
        else {
            array = obj;
        }
        return array;
    }

    function copyProperties(to, from) {
        if (to == null || from == null) {
            return;
        }
        for (var p in from) {
            if (from.hasOwnProperty(p)) {
                to[p] = from[p];
            }
        }
    }

    function getClass(basesArray) {
        for (var i = 0; i < basesArray.length; i++) {
            if (isNativeClass(basesArray[i]) || basesArray[i].$metadata$.type === Kotlin.TYPE.CLASS) {
                return basesArray[i];
            }
        }
        return null;
    }

    var emptyFunction = function() {
        return function() {};
    };

    Kotlin.TYPE = {
        CLASS: "class",
        TRAIT: "trait",
        OBJECT: "object",
        INIT_FUN: "init fun"
    };

    Kotlin.classCount = 0;
    Kotlin.newClassIndex = function() {
        var tmp = Kotlin.classCount;
        Kotlin.classCount++;
        return tmp;
    };

    function isNativeClass(obj) {
        return !(obj == null) && obj.$metadata$ == null;
    }

    function applyExtension(current, bases, baseGetter) {
        for (var i = 0; i < bases.length; i++) {
            if (isNativeClass(bases[i])) {
                continue;
            }
            var base = baseGetter(bases[i]);
            for (var p in  base) {
                if (base.hasOwnProperty(p)) {
                    if(!current.hasOwnProperty(p) || current[p].$classIndex$ < base[p].$classIndex$) {
                        current[p] = base[p];
                    }
                }
            }
        }
    }

    function computeMetadata(bases, properties) {
        var metadata = {};

        metadata.baseClasses = toArray(bases);
        metadata.baseClass = getClass(metadata.baseClasses);
        metadata.classIndex = Kotlin.newClassIndex();
        metadata.functions = {};
        metadata.properties = {};

        if (!(properties == null)) {
            for (var p in properties) {
                if (properties.hasOwnProperty(p)) {
                    var property = properties[p];
                    property.$classIndex$ = metadata.classIndex;
                    if (typeof property === "function") {
                        metadata.functions[p] = property;
                    } else {
                        metadata.properties[p] = property;
                    }
                }
            }
        }
        applyExtension(metadata.functions, metadata.baseClasses, function (it) {
            return it.$metadata$.functions
        });
        applyExtension(metadata.properties, metadata.baseClasses, function (it) {
            return it.$metadata$.properties
        });

        return metadata;
    }

    function class_object() {
        var object = this.object_initializer$();
        Object.defineProperty(this, "object", {value: object});
        return object;
    }

    function initClassObjectAndFixBaseInitializer(baseClass) {
        var object = baseClass.object;
        Object.defineProperty(this, "baseInitializer", {value: baseClass});
        Object.defineProperty(this, "object", {value: null, configurable: true});
        if (this.object_initializer$ != null) {
            return class_object.call(this);
        } else {
            return null;
        }
    }

    function addBaseInitializerAndClassObject(constr, baseClass) {
        if (baseClass == null) {
            baseClass = emptyFunction();
        }
        var initFunction = initClassObjectAndFixBaseInitializer.bind(constr, baseClass);
        Object.defineProperty(constr, "baseInitializer", {
            get: function() {
                initFunction();
                return baseClass;
            },
            configurable: true
        });
        Object.defineProperty(constr, "object", {get: initFunction, configurable: true});
    }

    function createDefaultConstructor() {
        return function $fun() {
            $fun.baseInitializer.call(this);
        }
    }

    Kotlin.createClassNow = function (bases, constructor, properties, staticProperties) {
        if (constructor == null) {
            constructor = createDefaultConstructor();
        }
        copyProperties(constructor, staticProperties);

        var metadata = computeMetadata(bases, properties);
        metadata.type = Kotlin.TYPE.CLASS;

        var prototypeObj;
        if (metadata.baseClass !== null) {
            prototypeObj = Object.create(metadata.baseClass.prototype);
        } else {
            prototypeObj = {};
        }
        Object.defineProperties(prototypeObj, metadata.properties);
        copyProperties(prototypeObj, metadata.functions);
        prototypeObj.constructor = constructor;

        constructor.$metadata$ = metadata;
        constructor.prototype = prototypeObj;
        addBaseInitializerAndClassObject(constructor, metadata.baseClass);
        return constructor;
    };

    Kotlin.createObjectNow = function (bases, constructor, functions) {
        var noNameClass = Kotlin.createClassNow(bases, constructor, functions);
        var obj = new noNameClass();
        obj.$metadata$ = {
            type: Kotlin.TYPE.OBJECT
        };
        return  obj;
    };

    Kotlin.createTraitNow = function (bases, properties, staticProperties) {
        var obj = function () {};
        copyProperties(obj, staticProperties);

        obj.$metadata$ = computeMetadata(bases, properties);
        obj.$metadata$.type = Kotlin.TYPE.TRAIT;

        obj.prototype = {};
        Object.defineProperties(obj.prototype, obj.$metadata$.properties);
        copyProperties(obj.prototype, obj.$metadata$.functions);
        Object.defineProperty(obj, "object", {get: class_object, configurable: true});
        return obj;
    };

    function getBases(basesFun) {
        if (typeof basesFun === "function") {
            return basesFun();
        } else {
            return basesFun;
        }
    }

    Kotlin.createClass = function (basesFun, constructor, properties, staticProperties) {
        function $o() {
            var klass = Kotlin.createClassNow(getBases(basesFun), constructor, properties, staticProperties);
            Object.defineProperty(this, $o.className, {value: klass});
            return klass;
        }
        $o.type = Kotlin.TYPE.INIT_FUN;
        return $o;
    };

    Kotlin.createTrait = function (basesFun, properties, staticProperties) {
        function $o() {
            var klass = Kotlin.createTraitNow(getBases(basesFun), properties, staticProperties);
            Object.defineProperty(this, $o.className, {value: klass});
            return klass;
        }
        $o.type = Kotlin.TYPE.INIT_FUN;
        return $o;
    };

    Kotlin.createObject = function (basesFun, constructor, functions) {
        return Kotlin.createObjectNow(getBases(basesFun), constructor, functions);
    };

    Kotlin.callGetter = function(thisObject, klass, propertyName) {
        return klass.$metadata$.properties[propertyName].get.call(thisObject);
    };

    Kotlin.callSetter = function(thisObject, klass, propertyName, value) {
        klass.$metadata$.properties[propertyName].set.call(thisObject, value);
    };

    function isInheritanceFromTrait (objConstructor, trait) {
        if (isNativeClass(objConstructor) || objConstructor.$metadata$.classIndex < trait.$metadata$.classIndex) {
            return false;
        }
        var baseClasses = objConstructor.$metadata$.baseClasses;
        var i;
        for (i = 0; i < baseClasses.length; i++) {
            if (baseClasses[i] === trait) {
                return true;
            }
        }
        for (i = 0; i < baseClasses.length; i++) {
            if (isInheritanceFromTrait(baseClasses[i], trait)) {
                return true;
            }
        }
        return false;
    }

    Kotlin.isType = function (object, klass) {
        if (object == null || klass == null) {
            return false;
        } else {
            if (object instanceof klass) {
                return true;
            }
            else if (isNativeClass(klass) || klass.$metadata$.type == Kotlin.TYPE.CLASS) {
                return false;
            }
            else {
                return isInheritanceFromTrait(object.constructor, klass);
            }
        }
    };


////////////////////////////////// packages & modules //////////////////////////////

    function getPackageObject(packageName, rootPackageObject) {
        if (packageName == null || packageName.length == 0) {
            return rootPackageObject;
        }
        var packages = packageName.split('.');
        var currentPackage = rootPackageObject;
        for (var i = 0; i < packages.length; i++) {
            var name = packages[i];
            if (typeof currentPackage[name] == "undefined") {
                currentPackage[name] = {};
            }
            currentPackage = currentPackage[name];
        }
        return currentPackage
    }

    function copyDefinition(members, packageObj, fileObject, fileInitFun) {
        if (members == null) {
            return;
        }
        for (var p in members) {
            if (members.hasOwnProperty(p)) {
                if (members[p] != null && members[p].type === Kotlin.TYPE.INIT_FUN) { // p is class/trait
                    members[p].className = p;
                    Object.defineProperty(packageObj, p, {
                        get: members[p],
                        configurable: true
                    });
                } else {
                    Object.defineProperty(packageObj, p, {get: fileInitFun.bind(null, p), configurable: true});
                    if (members[p] != null) {
                        fileObject[p] = members[p];
                    } else {
                        fileObject[p] = {value: void 0, writable: true};
                    }
                }
            }
        }
    }

    function createFileInitFun(packageObject, fileObject, initializer) {
        return function(propertyName) {
            for (var p in fileObject) {
                if (fileObject.hasOwnProperty(p)) {
                    if (typeof fileObject[p] === "function") {
                        Object.defineProperty(packageObject, p, {value: fileObject[p]});
                    } else {
                        Object.defineProperty(packageObject, p, fileObject[p]);
                    }
                }
            }
            initializer.call(packageObject);
            return packageObject[propertyName];
        }
    }

    Kotlin.addPackagePart = function (rootPackageObject, packageName, initializer, members) {
        if (initializer == null) {
            initializer = emptyFunction();
        }
        var packageObject = getPackageObject(packageName, rootPackageObject);
        var fileObject = {};
        var fileInitFun = createFileInitFun(packageObject, fileObject, initializer);
        copyDefinition(members, packageObject, fileObject, fileInitFun);
    };

    Kotlin.defineModule = function (id, declaration) {
        if (id in Kotlin.modules) {
            throw new Error("Module " + id + " is already defined");
        }
        Object.defineProperty(Kotlin.modules, id, {value: declaration});
    };

})();


