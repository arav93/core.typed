;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.core.typed.test.compiler
  (:refer-clojure :exclude [munge macroexpand-1])
  (:require [clojure.java.io :as io]
            [clojure.repl :refer [pst]]
            [clojure.string :as string]
            [clojure.core.typed :refer [def-alias ann declare-names check-ns ann-form tc-ignore
                                        fn> cf print-env
                                        ;types
                                        Atom1 AnyInteger Option]])
  (:import (java.lang StringBuilder)
           (java.io PushbackReader File)
           (clojure.lang Symbol IPersistentMap IPersistentSet Seqable IPersistentVector
                         Atom ISeq)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-alias NsEntry
  (HMap {:name Symbol}
        :optional
        {:defs (IPersistentMap Symbol Symbol)
         :uses (IPersistentMap Symbol Symbol)
         :excludes (IPersistentSet Symbol)
         :requires (IPersistentMap Symbol Symbol)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-alias Context (U (Value :statement)
                      (Value :expr)
                      (Value :return)))

(def-alias LocalBinding (HMap {:name Symbol}))

(def-alias Env
  (HMap {:locals (IPersistentMap Symbol LocalBinding)
         :context Context
         :line (U nil Number)
         :ns NsEntry}))

(declare-names Expr)
;syntax
(def-alias Form Any)

(def-alias FnMethod (HMap {:env Env
                           :variadic (U nil Expr)
                           :params (Seqable Any)
                           :max-fixed-arity Number
                           :gthis (U nil Symbol)
                           :recurs Any
                           :statements (Seqable Expr)
                           :ret Expr}))


(def-alias
  Expr
  (Rec [x]
       (U ;; if
          (HMap {:env Env
                 :op (Value :if)
                 :form Form
                 :test x
                 :then x
                 :else x
                 :unchecked Any
                 :children (Seqable x)})
          ;; throw
          (HMap {:env Env
                 :op (Value :throw)
                 :form Form
                 :throw x
                 :children (Seqable x)})
          ;; try
          (HMap {:env Env
                 :op (Value :try*)
                 :form Form
                 :try '{:statements (Option (Seqable x))
                        :ret x}
                 :catch (Option '{:statements (Option (Seqable x))
                                  :ret x})
                 :finally (Option '{:statements (Option (Seqable x))
                                    :ret x})
                 :name (Option String)
                 :children (Seqable x)})
          ;; def
          (HMap {:env Env
                 :op (Value :def)
                 :form Form
                 :name Symbol
                 :doc (U nil String)
                 :init (Option x)}
                :optional
                {:tag (Option Symbol)
                 :dynamic boolean
                 :export Any
                 :children (Seqable x)})
          ;; fn
          (HMap {:env Env
                 :op (Value :fn)
                 :form Form
                 :name Any
                 :methods (Seqable FnMethod)})
          ;; letfn
          (HMap {:env Env
                 :op (Value :letfn)
                 :bindings (IPersistentVector LocalBinding)
                 :statements (Seqable x)
                 :ret x
                 :children (Seqable x)})
          ;; do
          (HMap {:env Env
                 :op (Value :do)
                 :form Form
                 :children (Seqable x)
                 :statements (Seqable x)
                 :ret x})
          ;; let
          (HMap {:env Env
                 :op (Value :let)
                 :loop boolean
                 :bindings (Seqable LocalBinding)
                 :statements (Seqable x)
                 :ret x
                 :form Form
                 :children (Seqable x)})
          ;; recur
          (HMap {:env Env
                 :op (Value :recur)
                 :form Form
                 :frame Any
                 :exprs (Seqable x)
                 :children (Seqable x)})
          ;; quote
          (HMap {:env Env
                 :op (Value :constant)
                 :form Form})
          ;; new
          (HMap {:env Env
                 :op (Value :new)
                 :form Form
                 :ctor x
                 :args (Seqable x)
                 :children (Seqable x)})
          ;; no-op
          (HMap {:env Env
                 :op (Value :no-op)})
          ;; set!
          (HMap {:env Env
                 :op (Value :set!)
                 :form Form
                 :target x
                 :val x
                 :children (Seqable x)})
          ;; ns
          (HMap {:env Env
                 :op (Value :ns)
                 :form Form
                 :name Symbol
                 :uses Any ;?
                 :requires Any ;?
                 :uses-macros Any ;?
                 :requires-macros Any ;?
                 :excludes Any}) ;?
          ;; deftype
          (HMap {:env Env
                 :op (Value :deftype*)
                 :as Any
                 :t Any
                 :fields Any
                 :pmasks Any})
          ;; defrecord
          (HMap {:env Env
                 :op (Value :defrecord*)
                 :form Form
                 :t Any
                 :fields Any
                 :pmasks Any})
          ;; dot
          (HMap {:env Env
                 :op (Value :dot)
                 :form Form
                 :target x
                 :children (Seqable x)
                 :tag (Option Symbol)})
          ;; js
          (HMap {:env Env
                 :op (Value :js) 
                 :tag (Option Symbol)
                 :form Form
                 :children (Seqable x)}
                :optional
                {:segs (Seqable Any)
                 :args (Seqable x)
                 :code String})

          ;; var
          (HMap {:env Env
                 :op (Value :var) 
                 :info '{:name Symbol}
                 :children (Seqable x)})

          ;; meta
          (HMap {:env Env
                 :op (Value :meta) 
                 :expr x
                 :meta Any
                 :children (Seqable x)})

          ;; map
          (HMap {:env Env
                 :op (Value :map) 
                 :simple-keys? Any
                 :keys (Seqable x)
                 :vals (Seqable x)
                 :children (Seqable x)})

          ;; vector
          (HMap {:env Env
                 :op (Value :vector) 
                 :items (Seqable x)
                 :children (Seqable x)})

          ;; set
          (HMap {:env Env
                 :op (Value :set) 
                 :items (Seqable x)
                 :children (Seqable x)}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-alias NsAnalysis
  (HMap {:ns Symbol
         :provides (IPersistentVector Symbol)
         :requires (IPersistentSet Symbol)
         :file String}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(declare resolve-var)
(declare confirm-bindings)
(declare munge)
(declare ^:dynamic *cljs-file*)
;(require 'cljs.core)

(ann js-reserved (IPersistentSet String))
(def js-reserved
  #{"abstract" "boolean" "break" "byte" "case"
    "catch" "char" "class" "const" "continue"
    "debugger" "default" "delete" "do" "double"
    "else" "enum" "export" "extends" "final"
    "finally" "float" "for" "function" "goto" "if"
    "implements" "import" "in" "instanceof" "int"
    "interface" "let" "long" "native" "new"
    "package" "private" "protected" "public"
    "return" "short" "static" "super" "switch"
    "synchronized" "this" "throw" "throws"
    "transient" "try" "typeof" "var" "void"
    "volatile" "while" "with" "yield" "methods"})

(ann cljs-reserved-file-names (IPersistentSet String))
(def cljs-reserved-file-names #{"deps.cljs"})

(ann namespaces (Atom1 (IPersistentMap Symbol NsEntry)))
(defonce namespaces (atom (ann-form
                            '{cljs.core {:name cljs.core}
                              cljs.user {:name cljs.user}}
                            (IPersistentMap Symbol NsEntry))))

(ann reset-namespaces! [-> Any])
(defn reset-namespaces! []
  (reset! namespaces
    '{cljs.core {:name cljs.core}
      cljs.user {:name cljs.user}}))

(ann *cljs-ns* Symbol)
(def ^:dynamic *cljs-ns* 'cljs.user)

(ann *cljs-file* (U nil Symbol))
(def ^:dynamic *cljs-file* nil)

(ann *cljs-warn-on-undeclared* boolean)
(def ^:dynamic *cljs-warn-on-undeclared* false)

(ann *cljs-warn-on-redef* boolean)
(def ^:dynamic *cljs-warn-on-redef* true)

(ann *cljs-warn-on-dynamic* boolean)
(def ^:dynamic *cljs-warn-on-dynamic* true)

(ann *cljs-warn-on-fn-var* boolean)
(def ^:dynamic *cljs-warn-on-fn-var* true)

(ann *cljs-warn-fn-arity* boolean)
(def ^:dynamic *cljs-warn-fn-arity* true)

(ann *unchecked-if* (Atom1 boolean))
(def ^:dynamic *unchecked-if* (atom false))

(ann *cljs-static-fns* boolean)
(def ^:dynamic *cljs-static-fns* false)

(ann *position* (U nil (Atom1 '[AnyInteger AnyInteger])))
(def ^:dynamic *position* nil)

(defmacro ^:private debug-prn
  [& args]
  `(.println System/err (str ~@args)))

(ann warning [Env Any -> nil])
(defn warning [env s]
  (binding [*out* *err*]
    (println
     (str s (when (:line env)
       (str " at line " (:line env) " " *cljs-file*))))))

(ann munge (Fn [Symbol -> Symbol]
               [String -> String]))
(defn munge [s]
  (let [ss (str s)
        ms (if (.contains ss "]")
             (let [idx (inc (.lastIndexOf ss "]"))]
               (str (subs ss 0 idx)
                    (clojure.lang.Compiler/munge (subs ss idx))))
             (clojure.lang.Compiler/munge ss))
        ms (if (js-reserved ms) (str ms "$") ms)]
    (if (symbol? s)
      (symbol ms)
      ms)))

(ann confirm-var-exists [Env Symbol Symbol -> Any])
(defn confirm-var-exists [env prefix suffix]
  (when *cljs-warn-on-undeclared*
    (let [crnt-ns (-> env :ns :name)]
      (when (= prefix crnt-ns)
        (when-not (-> @namespaces crnt-ns :defs suffix)
          (warning env
            (str "WARNING: Use of undeclared Var " prefix "/" suffix)))))))

(ann resolve-ns-alias [Env (U String Symbol) -> Symbol])
(defn resolve-ns-alias [env name]
  (let [sym (symbol name)]
    (get (:requires (:ns env)) sym sym)))

(ann core-name? [Env Symbol -> Any])
(defn core-name?
  "Is sym visible from core in the current compilation namespace?"
  [env sym]
  (and (get (:defs (@namespaces 'cljs.core)) sym)
       (not (contains? (-> env :ns :excludes) sym))))

(ann js-var [Symbol -> String])
(defn js-var [sym]
  (let [parts (string/split (name sym) #"\.")
        first (first parts)
        step (fn [part] (str "['" part "']"))]
    (apply str first (map step (rest parts)))))

(ann resolve-existing-var [Env Symbol -> LocalBinding])
(tc-ignore
(defn resolve-existing-var [env sym]
  (if (= (namespace sym) "js")
    {:name (js-var sym) :ns 'js}
    (let [s (str sym)
          lb (-> env :locals sym)]
      (cond
        lb lb

        :else
        (if-let [ns (namespace sym)]
          (let [ns (if (= "clojure.core" ns) "cljs.core" ns)
                full-ns (resolve-ns-alias env ns)]
            (confirm-var-exists env full-ns (symbol (name sym)))
            (merge (get-in @namespaces [full-ns :defs (symbol (name sym))])
                   {:name (symbol (str full-ns "." (munge (name sym))))
                    :name-sym (symbol (str full-ns) (str (name sym)))
                    :ns full-ns}))

          (cond
            (.contains s ".")
            (let [idx (.indexOf s ".")
                  prefix (symbol (subs s 0 idx))
                  suffix (subs s idx)
                  lb (-> env :locals prefix)]
              (if lb
                {:name (munge (symbol (str (:name lb) suffix)))
                 :name-sym (symbol (str (:name lb) suffix))}
                (do
                  (confirm-var-exists env prefix (symbol suffix))
                  (merge (get-in @namespaces [prefix :defs (symbol suffix)])
                         {:name (munge sym)
                          :name-sym (symbol (str prefix) suffix)
                          :ns prefix}))))

            :else
            (if-let [full-ns (get-in @namespaces [(-> env :ns :name) :uses sym])]
              (merge
                (get-in @namespaces [full-ns :defs sym])
                {:name (symbol (str full-ns "." (munge (name sym))))
                 :name-sym (symbol (str full-ns) (str sym))
                 :ns (-> env :ns :name)})
              (let [full-ns (if (core-name? env sym)
                              'cljs.core
                              (-> env :ns :name))]
                (confirm-var-exists env full-ns sym)
                (merge (get-in @namespaces [full-ns :defs sym])
                       {:name (munge (symbol (str full-ns "." (munge (name sym)))))
                        :name-sym (symbol (str full-ns) (str sym))
                        :ns full-ns})))))))))
  )

(ann resolve-var [Env Symbol -> (HMap {:name Symbol})])
;need a NamespacedSymbol refinement, of which `namespace` is a predicate.
(tc-ignore
(defn resolve-var [env sym]
  (if (= (namespace sym) "js")
    {:name (js-var sym)}
    (let [s (str sym)
          lb (-> env :locals sym)]
      (cond
       lb lb

       (namespace sym)
       (let [ns (namespace sym)
             ns (if (= "clojure.core" ns) "cljs.core" ns)]
         {:name (symbol (str (resolve-ns-alias env ns) "." (munge (name sym))))})

       (.contains s ".")
       (let [idx (.indexOf s ".")
             prefix (symbol (subs s 0 idx))
             suffix (subs s idx)
             lb (-> env :locals prefix)]
         (if lb
           {:name (munge (symbol (str (:name lb) suffix)))}
           {:name (munge sym)}))

       :else
       (let [s (str (if (core-name? env sym)
                      'cljs.core
                      (-> env :ns :name))
                    "." (munge (name sym)))]
         {:name (munge (symbol s))})))))
  )

(ann confirm-bindings [Env (Seqable Symbol) -> Any])
(defn confirm-bindings [env names]
  (doseq [name names]
    (let [env (merge env {:ns (@namespaces *cljs-ns*)})
          ev (resolve-existing-var env name)]
      (when (and *cljs-warn-on-dynamic*
                 ;; don't warn on vars from other namespaces because
                 ;; dependency ordering happens *after* compilation
                 (= (:ns ev) *cljs-ns*)
                 ev (not (-> ev :dynamic)))
        (warning env
          (str "WARNING: " (:name-sym ev) " not declared ^:dynamic"))))))

(ann comma-sep [(Option (Seqable Any)) -> (Seqable Any)])
(defn- comma-sep [xs]
  (interpose "," xs))

(ann escape-char [Character -> (U Character String)])
(defn- escape-char [^Character c]
  (let [cp (.hashCode c)]
    (case cp
      ; Handle printable escapes before ASCII
      34 "\\\""
      92 "\\\\"
      ; Handle non-printable escapes
      8 "\\b"
      12 "\\f"
      10 "\\n"
      13 "\\r"
      9 "\\t"
      (if (< 31 cp 127)
        c ; Print simple ASCII characters
        (format "\\u%04X" cp))))) ; Any other character is Unicode

(ann escape-string [CharSequence -> String])
;doseq
(tc-ignore
(defn- escape-string [^CharSequence s]
  (let [sb (StringBuilder. (count s))]
    (doseq [c s]
      (.append sb (escape-char c)))
    (.toString sb)))
  )

(ann wrap-in-double-quotes [Any -> String])
(defn- wrap-in-double-quotes [x]
  (str \" x \"))

(ann emit [Expr -> nil])
(defmulti emit :op)

(ann emits [Any * -> nil])
;doseq
(tc-ignore
(defn emits [& xs]
  (doseq [x xs]
    (cond
      (nil? x) nil
      (map? x) (emit x)
      (seq? x) (apply emits x)
      (fn? x)  (x)
      :else (do
              (let [s (print-str x)]
                (when *position*
                  (swap! *position* (fn [[line column]]
                                      [line (+ column (count s))])))
                (print s)))))
  nil)
  )

(ann emit-str [Expr -> String])
(defn ^String emit-str [expr]
  (with-out-str (emit expr)))

(ann emitln [Any * -> nil])
;*position* is technically mutable! Cannot infer non-nil in test
(tc-ignore
(defn emitln [& xs]
  (apply emits xs)
  ;; Prints column-aligned line number comments; good test of *position*.
  ;(when *position*
  ;  (let [[line column] @*position*]
  ;    (print (apply str (concat (repeat (- 120 column) \space) ["// " (inc line)])))))
  (println)
  (when *position*
    (swap! *position* (fn> [[[line column] :- '[AnyInteger AnyInteger]]]
                        [(inc line) 0])))
  nil)
  )

(ann emit-constant [Any -> String])
(defmulti emit-constant class)
(defmethod emit-constant nil [x] (emits "null"))
(defmethod emit-constant Long [x] (emits x))
(defmethod emit-constant Integer [x] (emits x)) ; reader puts Integers in metadata
(defmethod emit-constant Double [x] (emits x))
(defmethod emit-constant String [x]
  (emits (wrap-in-double-quotes (escape-string x))))
(defmethod emit-constant Boolean [x] (emits (if x "true" "false")))
(defmethod emit-constant Character [x]
  (emits (wrap-in-double-quotes (escape-char x))))

;String as Seqable
(tc-ignore
(defmethod emit-constant java.util.regex.Pattern [x]
  (let [[_ flags pattern] (re-find #"^(?:\(\?([idmsux]*)\))?(.*)" (str x))]
    (emits \/ (.replaceAll (re-matcher #"/" pattern) "\\\\/") \/ flags)))
  )

(defmethod emit-constant clojure.lang.Keyword [x]
           (emits \" "\\uFDD0" \'
                  (if (namespace x)
                    (str (namespace x) "/") "")
                  (name x)
                  \"))

(defmethod emit-constant clojure.lang.Symbol [x]
           (emits \" "\\uFDD1" \'
                  (if (namespace x)
                    (str (namespace x) "/") "")
                  (name x)
                  \"))

(ann emit-meta-constant [Any Any * -> nil])
(defn- emit-meta-constant [x & body]
  (if (meta x)
    (do
      (emits "cljs.core.with_meta(" body ",")
      (emit-constant (meta x))
      (emits ")"))
    (emits body)))

;no defaults for parameters of (RClass PersistentList) etc, should be (PersistentList Any)
;variances?
(tc-ignore
(defmethod emit-constant clojure.lang.PersistentList$EmptyList [x]
  (emit-meta-constant x "cljs.core.List.EMPTY"))

(defmethod emit-constant clojure.lang.PersistentList [x]
  (emit-meta-constant x
    (concat ["cljs.core.list("]
            (comma-sep (map #(fn [] (emit-constant %)) x))
            [")"])))

(defmethod emit-constant clojure.lang.Cons [x]
  (emit-meta-constant x
    (concat ["cljs.core.list("]
            (comma-sep (map #(fn [] (emit-constant %)) x))
            [")"])))

(defmethod emit-constant clojure.lang.IPersistentVector [x]
  (emit-meta-constant x
    (concat ["cljs.core.vec(["]
            (comma-sep (map #(fn [] (emit-constant %)) x))
            ["])"])))

(defmethod emit-constant clojure.lang.IPersistentMap [x]
  (emit-meta-constant x
    (concat ["cljs.core.hash_map("]
            (comma-sep (map #(fn [] (emit-constant %))
                            (apply concat x)))
            [")"])))

(defmethod emit-constant clojure.lang.PersistentHashSet [x]
  (emit-meta-constant x
    (concat ["cljs.core.set(["]
            (comma-sep (map #(fn [] (emit-constant %)) x))
            ["])"])))
  )

(ann emit-block [Context (Option (Seqable Expr)) Expr -> nil])
(defn emit-block 
  [context statements ret]
  (when statements
    (emits statements))
  (emit ret))

(defmacro emit-wrap [env & body]
  `(let [env# ~env]
     (when (= :return (:context env#)) (emits "return "))
     ~@body
     (when-not (= :expr (:context env#)) (emitln ";"))))

(defmethod emit :no-op
  [m] (emits "void 0;"))

(defmethod emit :var
  [{:keys [info env] :as arg}]
  (print-env "info")
  (emit-wrap env (emits (munge (:name info)))))

(defmethod emit :meta
  [{:keys [expr meta env]}]
  (emit-wrap env
    (emits "cljs.core.with_meta(" expr "," meta ")")))

(ann array-map-threshold AnyInteger)
(def ^:private array-map-threshold 16)
(ann obj-map-threshold AnyInteger)
(def ^:private obj-map-threshold 32)

(defmethod emit :map
  [{:keys [env simple-keys? keys vals]}]
  (emit-wrap env
    (cond
      (and simple-keys? (<= (count keys) obj-map-threshold))
      (emits "cljs.core.ObjMap.fromObject(["
             (comma-sep keys) ; keys
             "],{"
             (comma-sep (map (fn> [[k :- Expr] [v :- Expr]]
                               (with-out-str (emit k) (print ":") (emit v)))
                             keys vals)) ; js obj
             "})")

      (<= (count keys) array-map-threshold)
      (emits "cljs.core.PersistentArrayMap.fromArrays(["
             (comma-sep keys)
             "],["
             (comma-sep vals)
             "])")

      :else
      (emits "cljs.core.PersistentHashMap.fromArrays(["
             (comma-sep keys)
             "],["
             (comma-sep vals)
             "])"))))

(defmethod emit :vector
  [{:keys [items env]}]
  (emit-wrap env
    (emits "cljs.core.PersistentVector.fromArray(["
           (comma-sep items) "])")))

(defmethod emit :set
  [{:keys [items env]}]
  (emit-wrap env
    (emits "cljs.core.set(["
           (comma-sep items) "])")))

(defmethod emit :constant
  [{:keys [form env]}]
  (when-not (= :statement (:context env))
    (emit-wrap env (emit-constant form))))

(ann get-tag [Expr -> (Option Symbol)])
(defn get-tag [e]
  (or (-> e :tag)
      (-> e :info :tag)))

(ann infer-tag [Expr -> (Option Symbol)])
;case needs improvements
(tc-ignore
(defn infer-tag [e]
  (if-let [tag (get-tag e)]
    tag
    (case (:op e)
      :let (infer-tag (:ret e))
      :if (let [then-tag (infer-tag (:then e))
                else-tag (infer-tag (:else e))]
            (when (= then-tag else-tag)
              then-tag))
      :constant (case (:form e)
                  true 'boolean
                  false 'boolean
                  nil)
      nil)))
  )

(ann safe-test? [Expr -> Any])
(defn safe-test? [e]
  (let [tag (infer-tag e)]
    (or (= tag 'boolean)
        (when (= (:op e) :constant)
          (let [form (:form e)]
            (not (or (and (string? form) (= form ""))
                     (and (number? form) (zero? form)))))))))

(defmethod emit :if
  [{:keys [test then else env unchecked]}]
  (let [context (:context env)
        checked (not (or unchecked (safe-test? test)))]
    (if (= :expr context)
      (emits "(" (when checked "cljs.core.truth_") "(" test ")?" then ":" else ")")
      (do
        (if checked
          (emitln "if(cljs.core.truth_(" test "))")
          (emitln "if(" test ")"))
        (emitln "{" then "} else")
        (emitln "{" else "}")))))

(defmethod emit :throw
  [{:keys [throw env]}]
  (if (= :expr (:context env))
    (emits "(function(){throw " throw "})()")
    (emitln "throw " throw ";")))

(ann emit-comment [Any Any -> nil])
;doseq
(tc-ignore
(defn emit-comment
  "Emit a nicely formatted comment string."
  [doc jsdoc]
  (let [docs (when doc [doc])
        docs (if jsdoc (concat docs jsdoc) docs)
        docs (remove nil? docs)]
    (letfn [(print-comment-lines [e] (doseq [next-line (string/split-lines e)]
                                       (emitln "* " (string/trim next-line))))]
      (when (seq docs)
        (emitln "/**")
        (doseq [e docs]
          (when e
            (print-comment-lines e)))
        (emitln "*/")))))
  )

(defmethod emit :def
  [{:keys [name init env doc export] :as expr}]
  (if init
    (do
      (emit-comment doc (:jsdoc init))
      (emits name)
      (emits " = " init)
      (when-not (= :expr (:context env)) (emitln ";"))
      (when export
        (emitln "goog.exportSymbol('" export "', " name ");")))
    (emitln "void 0;")))

(ann emit-apply-to ['{:name Symbol, :params (Seqable Any), :env Env} -> nil]) ;FIXME ?
;doseq
(tc-ignore
(defn emit-apply-to
  [{:keys [name params env]}]
  (let [arglist (gensym "arglist__")
        delegate-name (str name "__delegate")]
    (emitln "(function (" arglist "){")
    (doseq [[i param] (map-indexed vector (butlast params))]
      (emits "var " param " = cljs.core.first(")
      (dotimes [_ i] (emits "cljs.core.next("))
      (emits arglist ")")
      (dotimes [_ i] (emits ")"))
      (emitln ";"))
    (if (< 1 (count params))
      (do
        (emits "var " (last params) " = cljs.core.rest(")
        (dotimes [_ (- (count params) 2)] (emits "cljs.core.next("))
        (emits arglist)
        (dotimes [_ (- (count params) 2)] (emits ")"))
        (emitln ");")
        (emitln "return " delegate-name "(" (string/join ", " params) ");"))
      (do
        (emits "var " (last params) " = ")
        (emits "cljs.core.seq(" arglist ");")
        (emitln ";")
        (emitln "return " delegate-name "(" (string/join ", " params) ");")))
    (emits "})")))
  )

(ann emit-fn-method [FnMethod -> nil])
(defn emit-fn-method
  [{:keys [gthis name variadic params statements ret env recurs max-fixed-arity]}]
  (emit-wrap env
             (emitln "(function " name "(" (comma-sep params) "){")
             (when gthis
               (emitln "var " gthis " = this;"))
             (when recurs (emitln "while(true){"))
             (emit-block :return statements ret)
             (when recurs
               (emitln "break;")
               (emitln "}"))
             (emits "})")))

(ann emit-variadic-fn-method [FnMethod -> nil])
;assoc not working
(tc-ignore
(defn emit-variadic-fn-method
  [{:keys [gthis name variadic params statements ret env recurs max-fixed-arity] :as f}]
  (emit-wrap env
             (let [name (or name (gensym))
                   delegate-name (str name "__delegate")]
               (emitln "(function() { ")
               (emitln "var " delegate-name " = function (" (comma-sep params) "){")
               (when recurs (emitln "while(true){"))
               (emit-block :return statements ret)
               (when recurs
                 (emitln "break;")
                 (emitln "}"))
               (emitln "};")

               (emitln "var " name " = function (" (comma-sep
                                                     (if variadic
                                                       (concat (butlast params) ['var_args])
                                                       params)) "){")
               (when gthis
                 (emitln "var " gthis " = this;"))
               (when variadic
                 (emitln "var " (last params) " = null;")
                 (emitln "if (goog.isDef(var_args)) {")
                 (emitln "  " (last params) " = cljs.core.array_seq(Array.prototype.slice.call(arguments, " (dec (count params)) "),0);")
                 (emitln "} "))
               (emitln "return " delegate-name ".call(" (string/join ", " (cons "this" params)) ");")
               (emitln "};")

               (emitln name ".cljs$lang$maxFixedArity = " max-fixed-arity ";")
               (emits name ".cljs$lang$applyTo = ")
               (emit-apply-to (assoc f :name name))
               (emitln ";")
               (emitln name ".cljs$lang$arity$variadic = " delegate-name ";")
               (emitln "return " name ";")
               (emitln "})()"))))
  )

;weirdness with `filter`
(tc-ignore
(defmethod emit :fn
  [{:keys [name env methods max-fixed-arity variadic recur-frames loop-lets]}]
  ;;fn statements get erased, serve no purpose and can pollute scope if named
  (when-not (= :statement (:context env))
    (let [loop-locals (seq (concat
                            (mapcat :names (filter #(and % @(:flag %)) recur-frames))
                            (mapcat :names loop-lets)))]
      (when loop-locals
        (when (= :return (:context env))
            (emits "return "))
        (emitln "((function (" (comma-sep loop-locals) "){")
        (when-not (= :return (:context env))
            (emits "return ")))
      (if (= 1 (count methods))
        (if variadic
          (emit-variadic-fn-method (assoc (first methods) :name name))
          (emit-fn-method (assoc (first methods) :name name)))
        (let [has-name? (and name true)
              name (or name (gensym))
              maxparams (apply max-key count (map :params methods))
              mmap (into {}
                     (map (fn [method]
                            [(symbol (str name "__" (count (:params method))))
                             method])
                          methods))
              ms (sort-by #(-> % second :params count) (seq mmap))]
          (when (= :return (:context env))
            (emits "return "))
          (emitln "(function() {")
          (emitln "var " name " = null;")
          (doseq [[n meth] ms]
            (emits "var " n " = ")
            (if (:variadic meth)
              (emit-variadic-fn-method meth)
              (emit-fn-method meth))
            (emitln ";"))
            (emitln name " = function(" (comma-sep (if variadic
                                                     (concat (butlast maxparams) ['var_args])
                                                     maxparams)) "){")
          (when variadic
            (emitln "var " (last maxparams) " = var_args;"))
          (emitln "switch(arguments.length){")
          (doseq [[n meth] ms]
            (if (:variadic meth)
              (do (emitln "default:")
                  (emitln "return " n ".cljs$lang$arity$variadic("
                          (comma-sep (butlast maxparams))
                          (and (> (count maxparams) 1) ", ")
                          "cljs.core.array_seq(arguments, " max-fixed-arity "));"))
              (let [pcnt (count (:params meth))]
                (emitln "case " pcnt ":")
                (emitln "return " n ".call(this" (if (zero? pcnt) nil
                                                     (list "," (comma-sep (take pcnt maxparams)))) ");"))))
          (emitln "}")
          (emitln "throw('Invalid arity: ' + arguments.length);")
          (emitln "};")
          (when variadic
            (emitln name ".cljs$lang$maxFixedArity = " max-fixed-arity ";")
            (emitln name ".cljs$lang$applyTo = " (some #(let [[n m] %] (when (:variadic m) n)) ms) ".cljs$lang$applyTo;"))
          (when has-name?
            (doseq [[n meth] ms]
              (let [c (count (:params meth))]
                (if (:variadic meth)
                  (emitln name ".cljs$lang$arity$variadic = " n ".cljs$lang$arity$variadic;")
                  (emitln name ".cljs$lang$arity$" c " = " n ";")))))
          (emitln "return " name ";")
          (emitln "})()")))
      (when loop-locals
        (emitln ";})(" (comma-sep loop-locals) "))")))))
  )

(defmethod emit :do
  [{:keys [statements ret env]}]
  (let [context (:context env)]
    (when (and statements (= :expr context)) (emits "(function (){"))
    ;(when statements (emitln "{"))
    (emit-block context statements ret)
    ;(when statements (emits "}"))
    (when (and statements (= :expr context)) (emits "})()"))))

(defmethod emit :try*
  [{:keys [env try catch name finally]}]
  (let [context (:context env)
        subcontext (if (= :expr context) :return context)]
    (if (or name finally)
      (do
        (when (= :expr context) (emits "(function (){"))
        (emits "try{")
        (let [{:keys [statements ret]} try]
          (emit-block subcontext statements ret))
        (emits "}")
        (when name
          (emits "catch (" name "){")
          (when catch
            (let [{:keys [statements ret]} catch]
              (emit-block subcontext statements ret)))
          (emits "}"))
        (when finally
          (let [{:keys [statements ret]} finally]
            (assert (not= :constant (:op ret)) "finally block cannot contain constant")
            (emits "finally {")
            (emit-block subcontext statements ret)
            (emits "}")))
        (when (= :expr context) (emits "})()")))
      (let [{:keys [statements ret]} try]
        (when (and statements (= :expr context)) (emits "(function (){"))
        (emit-block subcontext statements ret)
        (when (and statements (= :expr context)) (emits "})()"))))))

;doseq
(tc-ignore
(defmethod emit :let
  [{:keys [bindings statements ret env loop]}]
  (let [context (:context env)]
    (when (= :expr context) (emits "(function (){"))
    (doseq [{:keys [name init]} bindings]
      (emitln "var " name " = " init ";"))
    (when loop (emitln "while(true){"))
    (emit-block (if (= :expr context) :return context) statements ret)
    (when loop
      (emitln "break;")
      (emitln "}"))
    ;(emits "}")
    (when (= :expr context) (emits "})()"))))
  )

;dotimes
(defmethod emit :recur
  [{:keys [frame exprs env]}]
  (let [temps (vec (take (count exprs) (repeatedly gensym)))
        names (:names frame)]
    (emitln "{")
    (dotimes [i (count exprs)]
      (emitln "var " (temps i) " = " (exprs i) ";"))
    (dotimes [i (count exprs)]
      (emitln (names i) " = " (temps i) ";"))
    (emitln "continue;")
    (emitln "}")))

;doseq
(tc-ignore
(defmethod emit :letfn
  [{:keys [bindings statements ret env]}]
  (let [context (:context env)]
    (when (= :expr context) (emits "(function (){"))
    (doseq [{:keys [name init]} bindings]
      (emitln "var " name " = " init ";"))
    (emit-block (if (= :expr context) :return context) statements ret)
    (when (= :expr context) (emits "})()"))))
  )

;update-in
(tc-ignore
(defmethod emit :invoke
  [{:keys [f args env]}]
  (let [fn? (and *cljs-static-fns*
                 (not (-> f :info :dynamic))
                 (-> f :info :fn-var))
        js? (= (-> f :info :ns) 'js)
        [f variadic-invoke]
        (if fn?
          (let [info (-> f :info)
                arity (count args)
                variadic? (:variadic info)
                mps (:method-params info)
                mfa (:max-fixed-arity info)]
            (cond
             ;; if only one method, no renaming needed
             (and (not variadic?)
                  (= (count mps) 1))
             [f nil]

             ;; direct dispatch to variadic case
             (and variadic? (> arity mfa))
             [(update-in f [:info :name]
                             (fn [name] (symbol (str name ".cljs$lang$arity$variadic"))))
              {:max-fixed-arity mfa}]

             ;; direct dispatch to specific arity case
             :else
             (let [arities (map count mps)]
               (if (some #{arity} arities)
                 [(update-in f [:info :name]
                             (fn [name] (symbol (str name ".cljs$lang$arity$" arity)))) nil]
                 [f nil]))))
          [f nil])]
    (emit-wrap env
      (cond
       variadic-invoke
       (let [mfa (:max-fixed-arity variadic-invoke)]
        (emits f "(" (comma-sep (take mfa args))
               (when-not (zero? mfa) ",")
               "cljs.core.array_seq([" (comma-sep (drop mfa args)) "], 0))"))
       
       (or fn? js?)
       (emits f "(" (comma-sep args)  ")")
       
       :else
       (emits f ".call(" (comma-sep (cons "null" args)) ")")))))
  )

(defmethod emit :new
  [{:keys [ctor args env]}]
  (emit-wrap env
             (emits "(new " ctor "("
                    (comma-sep args)
                    "))")))

(defmethod emit :set!
  [{:keys [target val env]}]
  (emit-wrap env (emits target " = " val)))

;doseq
(tc-ignore
(defmethod emit :ns
  [{:keys [name requires uses requires-macros env]}]
  (emitln "goog.provide('" (munge name) "');")
  (when-not (= name 'cljs.core)
    (emitln "goog.require('cljs.core');"))
  (doseq [lib (into (vals requires) (distinct (vals uses)))]
    (emitln "goog.require('" (munge lib) "');")))
  )

;doseq
(tc-ignore
(defmethod emit :deftype*
  [{:keys [t fields pmasks]}]
  (let [fields (map munge fields)]
    (emitln "")
    (emitln "/**")
    (emitln "* @constructor")
    (emitln "*/")
    (emitln t " = (function (" (comma-sep (map str fields)) "){")
    (doseq [fld fields]
      (emitln "this." fld " = " fld ";"))
    (doseq [[pno pmask] pmasks]
      (emitln "this.cljs$lang$protocol_mask$partition" pno "$ = " pmask ";"))
    (emitln "})")))
  )

;doseq
(tc-ignore
(defmethod emit :defrecord*
  [{:keys [t fields pmasks]}]
  (let [fields (concat (map munge fields) '[__meta __extmap])]
    (emitln "")
    (emitln "/**")
    (emitln "* @constructor")
    (doseq [fld fields]
      (emitln "* @param {*} " fld))
    (emitln "* @param {*=} __meta ")
    (emitln "* @param {*=} __extmap")
    (emitln "*/")
    (emitln t " = (function (" (comma-sep (map str fields)) "){")
    (doseq [fld fields]
      (emitln "this." fld " = " fld ";"))
    (doseq [[pno pmask] pmasks]
      (emitln "this.cljs$lang$protocol_mask$partition" pno "$ = " pmask ";"))
    (emitln "if(arguments.length>" (- (count fields) 2) "){")
    (emitln "this.__meta = __meta;")
    (emitln "this.__extmap = __extmap;")
    (emitln "} else {")
    (emits "this.__meta=")
    (emit-constant nil)
    (emitln ";")
    (emits "this.__extmap=")
    (emit-constant nil)
    (emitln ";")
    (emitln "}")
    (emitln "})")))
  )

(defmethod emit :dot
  [{:keys [target field method args env]}]
  (emit-wrap env
             (if field
               (emits target "." field)
               (emits target "." method "("
                      (comma-sep args)
                      ")"))))

(defmethod emit :js
  [{:keys [env code segs args]}]
  (emit-wrap env
             (if code
               (emits code)
               (emits (interleave (concat segs (repeat nil))
                                  (concat args [nil]))))))

(declare analyze analyze-symbol analyze-seq)

(ann specials (IPersistentSet Symbol))
(def specials '#{if def fn* do let* loop* letfn* throw try* recur new set! ns deftype* defrecord* . js* & quote})

(def-alias RecurFrame (HMap {:names (Seqable Symbol)
                             :flag (Atom1 Any)}))
(ann *recur-frames* (U nil (Seqable RecurFrame)))
(def ^:dynamic *recur-frames* nil)

(def-alias LoopLet (HMap {:names (Seqable Symbol)}))

(ann *loop-lets* (U nil (Seqable LoopLet)))
(def ^:dynamic *loop-lets* nil)

(defmacro disallowing-recur [& body]
  `(binding [*recur-frames* (cons nil *recur-frames*)] ~@body))

(ann analyze-block [Env (Seqable Any) -> (HMap {:statements (Seqable Expr)
                                                :ret Expr})])
(defn analyze-block
  "returns {:statements .. :ret ..}"
  [env exprs]
  (let [statements (disallowing-recur
                     (seq (map #(analyze (assoc env :context :statement) %) (butlast exprs))))
        ret (if (<= (count exprs) 1)
              (analyze env (first exprs))
              (analyze (assoc env :context (if (= :statement (:context env)) :statement :return)) (last exprs)))]
    {:statements statements :ret ret}))

(declare-names SpecialForm)

#_(def-alias IfForm
  (U (Seq* (Value 'if) Form)
     (Seq* (Value 'if) Form Form)))

#_(def-alias ThrowForm
  (Seq* (Value 'throw) Form))

#_(def-alias Try*Form
  (Seq* (Value 'try*) Form *)) ;TODO "rest" Seqs

#_(def-alias Def*Form
  (U (Seq* (Value 'def) Symbol)
     (Seq* (Value 'def) Symbol x)
     (Seq* (Value 'def) Symbol String x)))

#_(def-alias Fn*Form
  (U
    (Seq* (Value 'fn*) (IPersistentVector Symbol) Form *)
    (Seq* (Value 'fn*) (IPersistentVector Symbol) (IPersistentMap Form Form) Form Form *)
    (Seq* (Value 'fn*) (Seq* (IPersistentVector Symbol) Form *) *)
    (Seq* (Value 'fn*) (Seq* (IPersistentVector Symbol) (IPersistentMap Form Form) Form Form *) *)
    (Seq* (Value 'fn*) Symbol (IPersistentVector Symbol) Form *)
    (Seq* (Value 'fn*) Symbol (IPersistentVector Symbol) (IPersistentMap Form Form) Form Form *)
    (Seq* (Value 'fn*) Symbol (Seq* (IPersistentVector Symbol) Form *) *)
    (Seq* (Value 'fn*) Symbol (Seq* (IPersistentVector Symbol) (IPersistentMap Form Form) Form Form *) *)))

#_(def-alias LetFn*Form
  (Seq* (Value 'letfn*) (Vector* Symbol Form *2) Form *))

#_(def-alias DoForm
  (Seq* (Value 'do) Form *))

#_(def-alias Let*Form
  (Seq* (Value 'let*) (Vector* Symbol Form *2) Form *))

#_(def-alias Loop*Form
  (Seq* (Value 'loop*) (Vector* Symbol Form *2) Form *))

#_(def-alias RecurForm
  (Seq* (Value 'recur) Form *))

#_(def-alias QuoteForm
  (Seq* (Value 'quote) Form))

#_(def-alias NewForm
  (Seq* (Value 'new) Form Form *))

#_(def-alias Set!Form
  (Set* (Value 'set!) Form Form))

#_(def-alias NsForm
  Any) ; TODO

#_(def-alias Deftype*Form
  (Seq* (Value 'deftype*) (IPersistentVector Symbol) Any)) ;TODO

#_(def-alias Defrecord*Form
  (Seq* (Value 'defrecord*) (IPersistentVector Symbol) Any)) ;TODO

#_(def-alias DotForm
  (Seq* (Value '.) Form Form *)) ;Rest args correct?

#_(def-alias Js*Form
  (Seq* (Value 'js*) String Form *))

#_(def-alias SpecialForm
  (U IfForm ThrowForm Try*Form Def*Form Fn*Form LetFn*Form DoForm Let*Form Loop*Form
     RecurForm QuoteForm NewForm Set!Form NsForm Deftype*Form Defrecord*Form DotForm
     Js*Form ))

#_(def-alias InvokeForm
  (I (Seq* Form Form *)
     (not SpecialForm)))

#_(def-alias Form
  (U SpecialForm
     InvokeForm
     ; Other literals
     Symbol (IPersistentMap Form Form) (IPersistentVector Form) (IPersistentSet Form)
     ; Hmm other unspecified constants go here...
     ))

#_(ann parse 
     (Fn [(Value 'if) Env IfForm (U nil Symbol) -> Expr]
         [(Value 'throw) Env ThrowForm (U nil Symbol) -> Expr]
         [(Value 'try*) Env Try*Form (U nil Symbol) -> Expr]
         [(Value 'def*) Env Def*Form (U nil Symbol) -> Expr]
         [(Value 'fn*) Env Fn*Form (U nil Symbol) -> Expr]
         [(Value 'letfn*) Env LetFn*Form (U nil Symbol) -> Expr]
         [(Value 'do) Env DoForm (U nil Symbol) -> Expr]
         [(Value 'let*) Env Let*Form (U nil Symbol) -> Expr]
         [(Value 'loop*) Env Loop*Form (U nil Symbol) -> Expr]
         [(Value 'recur) Env RecurForm (U nil Symbol) -> Expr]
         [(Value 'quote) Env QuoteForm (U nil Symbol) -> Expr]
         [(Value 'new) Env NewForm (U nil Symbol) -> Expr]
         [(Value 'set!) Env Set!Form (U nil Symbol) -> Expr]
         [(Value 'ns) Env NsForm (U nil Symbol) -> Expr]
         [(Value 'deftype*) Env Deftype*Form (U nil Symbol) -> Expr]
         [(Value 'defrecord*) Env Defrecord*Form (U nil Symbol) -> Expr]
         [(Value '.) Env DotForm (U nil Symbol) -> Expr]
         [(Value 'js*) Env Js*Form (U nil Symbol) -> Expr]))

(defmulti parse (fn [op & rest] op))

(defmethod parse 'if
  [op env [_ test then else :as form] name]
  (let [test-expr (disallowing-recur (analyze (assoc env :context :expr) test))
        then-expr (analyze env then)
        else-expr (analyze env else)]
    {:env env :op :if :form form
     :test test-expr :then then-expr :else else-expr
     :unchecked @*unchecked-if*
     :children [test-expr then-expr else-expr]}))

(defmethod parse 'throw
  [op env [_ throw :as form] name]
  (let [throw-expr (disallowing-recur (analyze (assoc env :context :expr) throw))]
    {:env env :op :throw :form form
     :throw throw-expr
     :children [throw-expr]}))

(defn- block-children [{:keys [statements ret]}]
  (conj (vec statements) ret))

(defmethod parse 'try*
  [op env [_ & body :as form] name]
  (let [body (vec body)
        catchenv (update-in env [:context] #(if (= :expr %) :return %))
        tail (peek body)
        fblock (when (and (seq? tail) (= 'finally (first tail)))
                  (rest tail))
        finally (when fblock
                  (analyze-block
                   (assoc env :context :statement)
                    fblock))
        body (if finally (pop body) body)
        tail (peek body)
        cblock (when (and (seq? tail)
                          (= 'catch (first tail)))
                 (rest tail))
        name (first cblock)
        locals (:locals catchenv)
        mname (when name (munge name))
        locals (if name
                 (assoc locals name {:name mname})
                 locals)
        catch (when cblock
                (analyze-block (assoc catchenv :locals locals) (rest cblock)))
        body (if name (pop body) body)
        try (when body
              (analyze-block (if (or name finally) catchenv env) body))]
    (when name (assert (not (namespace name)) "Can't qualify symbol in catch"))
    {:env env :op :try* :form form
     :try try
     :finally finally
     :name mname
     :catch catch
     :children (vec (mapcat block-children
                            [try catch finally]))}))

(defmethod parse 'def
  [op env form name]
  (let [pfn (fn
              ([_ sym] {:sym sym})
              ([_ sym init] {:sym sym :init init})
              ([_ sym doc init] {:sym sym :doc doc :init init}))
        args (apply pfn form)
        sym (:sym args)
        tag (-> sym meta :tag)
        dynamic (-> sym meta :dynamic)
        ns-name (-> env :ns :name)]
    (assert (not (namespace sym)) "Can't def ns-qualified name")
    (let [env (if (or (and (not= ns-name 'cljs.core)
                           (core-name? env sym))
                      (get-in @namespaces [ns-name :uses sym]))
                (let [ev (resolve-existing-var (dissoc env :locals) sym)]
                  (when *cljs-warn-on-redef*
                    (warning env
                      (str "WARNING: " sym " already refers to: " (symbol (str (:ns ev)) (str sym))
                           " being replaced by: " (symbol (str ns-name) (str sym)))))
                  (swap! namespaces update-in [ns-name :excludes] conj sym)
                  (update-in env [:ns :excludes] conj sym))
                env)
          name (munge (:name (resolve-var (dissoc env :locals) sym)))
          init-expr (when (contains? args :init)
                      (disallowing-recur
                       (analyze (assoc env :context :expr) (:init args) sym)))
          fn-var? (and init-expr (= (:op init-expr) :fn))
          export-as (when-let [export-val (-> sym meta :export)]
                      (if (= true export-val) name export-val))
          doc (or (:doc args) (-> sym meta :doc))]
      (when-let [v (get-in @namespaces [ns-name :defs sym])]
        (when (and *cljs-warn-on-fn-var*
                   (not (-> sym meta :declared))
                   (and (:fn-var v) (not fn-var?)))
          (warning env
            (str "WARNING: " (symbol (str ns-name) (str sym))
                 " no longer fn, references are stale"))))
      (swap! namespaces update-in [ns-name :defs sym]
             (fn [m]
               (let [m (assoc (or m {}) :name name)]
                 (merge m
                   (when tag {:tag tag})
                   (when dynamic {:dynamic true})
                   (when-let [line (:line env)]
                     {:file *cljs-file* :line line})
                   (when fn-var?
                     {:fn-var true
                      :variadic (:variadic init-expr)
                      :max-fixed-arity (:max-fixed-arity init-expr)
                      :method-params (map (fn [m]
                                            (:params m))
                                          (:methods init-expr))})))))
      (merge {:env env :op :def :form form
              :name name :doc doc :init init-expr}
             (when tag {:tag tag})
             (when dynamic {:dynamic true})
             (when export-as {:export export-as})
             (when init-expr {:children [init-expr]})))))

(ann analyze-fn-method [Env LocalBinding (Seqable Any) -> FnMethod])
(defn- analyze-fn-method [env locals meth]
  (letfn [(uniqify [[p & r]]
            (when p
              (cons (if (some #{p} r) (gensym (str p)) p)
                    (uniqify r))))]
   (let [params (first meth)
         fields (-> params meta ::fields)
         variadic (boolean (some '#{&} params))
         params (uniqify (remove '#{&} params))
         fixed-arity (count (if variadic (butlast params) params))
         body (next meth)
         gthis (and fields (gensym "this__"))
         locals (reduce (fn [m fld]
                          (assoc m fld
                                 {:name (symbol (str gthis "." (munge fld)))
                                  :field true
                                  :mutable (-> fld meta :mutable)}))
                        locals fields)
         locals (reduce (fn [m name] (assoc m name {:name (munge name)})) locals params)
         recur-frame {:names (vec (map munge params)) :flag (atom nil)}
         block (binding [*recur-frames* (cons recur-frame *recur-frames*)]
                 (analyze-block (assoc env :context :return :locals locals) body))]
     (merge {:env env :variadic variadic :params (map munge params) :max-fixed-arity fixed-arity
             :gthis gthis :recurs @(:flag recur-frame)}
            block))))

(defmethod parse 'fn*
  [op env [_ & args :as form] name]
  (let [[name meths] (if (symbol? (first args))
                       [(first args) (next args)]
                       [name (seq args)])
        ;;turn (fn [] ...) into (fn ([]...))
        meths (if (vector? (first meths)) (list meths) meths)
        mname (when name (munge name))
        locals (:locals env)
        locals (if name (assoc locals name {:name mname}) locals)
        menv (if (> (count meths) 1) (assoc env :context :expr) env)
        methods (map #(analyze-fn-method menv locals %) meths)
        max-fixed-arity (apply max (map :max-fixed-arity methods))
        variadic (boolean (some :variadic methods))]
    ;;todo - validate unique arities, at most one variadic, variadic takes max required args
    {:env env :op :fn :form form :name mname :methods methods :variadic variadic
     :recur-frames *recur-frames* :loop-lets *loop-lets*
     :jsdoc [(when variadic "@param {...*} var_args")]
     :max-fixed-arity max-fixed-arity
     :children (vec (mapcat block-children
                            methods))}))

(defmethod parse 'letfn*
  [op env [_ bindings & exprs :as form] name]
  (assert (and (vector? bindings) (even? (count bindings))) "bindings must be vector of even number of elements")
  (let [n->fexpr (into {} (map (juxt first second) (partition 2 bindings)))
        names    (keys n->fexpr)
        n->gsym  (into {} (map (juxt identity #(gensym (str (munge %) "__"))) names))
        gsym->n  (into {} (map (juxt n->gsym identity) names))
        context  (:context env)
        bes      (reduce (fn [bes n]
                           (let [g (n->gsym n)]
                             (conj bes {:name  g
                                        :tag   (-> n meta :tag)
                                        :local true})))
                         []
                         names)
        meth-env (reduce (fn [env be]
                           (let [n (gsym->n (be :name))]
                             (assoc-in env [:locals n] be)))
                         (assoc env :context :expr)
                         bes)
        [meth-env finits]
        (reduce (fn [[env finits] n]
                  (let [finit (analyze meth-env (n->fexpr n))
                        be (-> (get-in env [:locals n])
                               (assoc :init finit))]
                    [(assoc-in env [:locals n] be)
                     (conj finits finit)]))
                [meth-env []]
                names)
        {:keys [statements ret]}
        (analyze-block (assoc meth-env :context (if (= :expr context) :return context)) exprs)
        bes (vec (map #(get-in meth-env [:locals %]) names))]
    {:env env :op :letfn :bindings bes :statements statements :ret ret :form form
     :children (into (vec (map :init bes))
                     (conj (vec statements) ret))}))

(defmethod parse 'do
  [op env [_ & exprs :as form] _]
  (let [block (analyze-block env exprs)]
    (merge {:env env :op :do :form form :children (block-children block)} block)))

(defn analyze-let
  [encl-env [_ bindings & exprs :as form] is-loop]
  (assert (and (vector? bindings) (even? (count bindings))) "bindings must be vector of even number of elements")
  (let [context (:context encl-env)
        [bes env]
        (disallowing-recur
         (loop [bes []
                env (assoc encl-env :context :expr)
                bindings (seq (partition 2 bindings))]
           (if-let [[name init] (first bindings)]
             (do
               (assert (not (or (namespace name) (.contains (str name) "."))) (str "Invalid local name: " name))
               (let [init-expr (analyze env init)
                     be {:name (gensym (str (munge name) "__"))
                         :init init-expr
                         :tag (or (-> name meta :tag)
                                  (-> init-expr :tag))
                         :local true}]
                 (recur (conj bes be)
                        (assoc-in env [:locals name] be)
                        (next bindings))))
             [bes env])))
        recur-frame (when is-loop {:names (vec (map :name bes)) :flag (atom nil)})
        {:keys [statements ret]}
        (binding [*recur-frames* (if recur-frame (cons recur-frame *recur-frames*) *recur-frames*)
                  *loop-lets* (cond
                               is-loop (or *loop-lets* ())
                               *loop-lets* (cons {:names (vec (map :name bes))} *loop-lets*))]
          (analyze-block (assoc env :context (if (= :expr context) :return context)) exprs))]
    {:env encl-env :op :let :loop is-loop
     :bindings bes :statements statements :ret ret :form form
     :children (into (vec (map :init bes))
                     (conj (vec statements) ret))}))

(defmethod parse 'let*
  [op encl-env form _]
  (analyze-let encl-env form false))

(defmethod parse 'loop*
  [op encl-env form _]
  (analyze-let encl-env form true))

(defmethod parse 'recur
  [op env [_ & exprs :as form] _]
  (let [context (:context env)
        frame (first *recur-frames*)
        exprs (disallowing-recur (vec (map #(analyze (assoc env :context :expr) %) exprs)))]
    (assert frame "Can't recur here")
    (assert (= (count exprs) (count (:names frame))) "recur argument count mismatch")
    (reset! (:flag frame) true)
    (assoc {:env env :op :recur :form form}
      :frame frame
      :exprs exprs
      :children exprs)))

(defmethod parse 'quote
  [_ env [_ x] _]
  {:op :constant :env env :form x})

(defmethod parse 'new
  [_ env [_ ctor & args :as form] _]
  (disallowing-recur
   (let [enve (assoc env :context :expr)
         ctorexpr (analyze enve ctor)
         argexprs (vec (map #(analyze enve %) args))]
     {:env env :op :new :form form :ctor ctorexpr :args argexprs
      :children (into [ctorexpr] argexprs)})))

(defmethod parse 'set!
  [_ env [_ target val alt :as form] _]
  (let [[target val] (if alt
                       ;; (set! o -prop val)
                       [`(. ~target ~val) alt]
                       [target val])]
    (disallowing-recur
     (let [enve (assoc env :context :expr)
           targetexpr (cond
                       ;; TODO: proper resolve
                       (= target '*unchecked-if*)
                       (do
                         (reset! *unchecked-if* val)
                         ::set-unchecked-if)

                       (symbol? target)
                       (do
                         (let [local (-> env :locals target)]
                           (assert (or (nil? local)
                                       (and (:field local)
                                            (:mutable local)))
                                   "Can't set! local var or non-mutable field"))
                         (analyze-symbol enve target))

                       :else
                       (when (seq? target)
                         (let [targetexpr (analyze-seq enve target nil)]
                           (when (:field targetexpr)
                             targetexpr))))
           valexpr (analyze enve val)]
       (assert targetexpr "set! target must be a field or a symbol naming a var")
       (cond
        (= targetexpr ::set-unchecked-if) {:env env :op :no-op}
        :else {:env env :op :set! :form form :target targetexpr :val valexpr
               :children [targetexpr valexpr]})))))

(defn ns->relpath [s]
  (str (string/replace (munge s) \. \/) ".cljs"))

(declare analyze-file)

(defn analyze-deps [deps]
  (doseq [dep deps]
    (when-not (:defs (@namespaces dep))
      (let [relpath (ns->relpath dep)]
        (when (io/resource relpath)
          (analyze-file relpath))))))

(defmethod parse 'ns
  [_ env [_ name & args :as form] _]
  (let [docstring (if (string? (first args)) (first args) nil)
        args      (if docstring (next args) args)
        excludes
        (reduce (fn [s [k exclude xs]]
                  (if (= k :refer-clojure)
                    (do
                      (assert (= exclude :exclude) "Only [:refer-clojure :exclude [names]] form supported")
                      (into s xs))
                    s))
                #{} args)
        deps (atom #{})
        {uses :use requires :require uses-macros :use-macros requires-macros :require-macros :as params}
        (reduce (fn [m [k & libs]]
                  (assert (#{:use :use-macros :require :require-macros} k)
                          "Only :refer-clojure, :require, :require-macros, :use and :use-macros libspecs supported")
                  (assoc m k (into {}
                                   (mapcat (fn [[lib kw expr]]
                                             (swap! deps conj lib)
                                             (case k
                                               (:require :require-macros)
                                               (do (assert (and expr (= :as kw))
                                                           "Only (:require [lib.ns :as alias]*) form of :require / :require-macros is supported")
                                                   [[expr lib]])
                                               (:use :use-macros)
                                               (do (assert (and expr (= :only kw))
                                                           "Only (:use [lib.ns :only [names]]*) form of :use / :use-macros is supported")
                                                   (map vector expr (repeat lib)))))
                                           libs))))
                {} (remove (fn [[r]] (= r :refer-clojure)) args))]
    (when (seq @deps)
      (analyze-deps @deps))
    (set! *cljs-ns* name)
    (require 'cljs.core)
    (doseq [nsym (concat (vals requires-macros) (vals uses-macros))]
      (clojure.core/require nsym))
    (swap! namespaces #(-> %
                           (assoc-in [name :name] name)
                           (assoc-in [name :excludes] excludes)
                           (assoc-in [name :uses] uses)
                           (assoc-in [name :requires] requires)
                           (assoc-in [name :uses-macros] uses-macros)
                           (assoc-in [name :requires-macros]
                                     (into {} (map (fn [[alias nsym]]
                                                     [alias (find-ns nsym)])
                                                   requires-macros)))))
    {:env env :op :ns :form form :name name :uses uses :requires requires
     :uses-macros uses-macros :requires-macros requires-macros :excludes excludes}))

(defmethod parse 'deftype*
  [_ env [_ tsym fields pmasks :as form] _]
  (let [t (munge (:name (resolve-var (dissoc env :locals) tsym)))]
    (swap! namespaces update-in [(-> env :ns :name) :defs tsym]
           (fn [m]
             (let [m (assoc (or m {}) :name t)]
               (if-let [line (:line env)]
                 (-> m
                     (assoc :file *cljs-file*)
                     (assoc :line line))
                 m))))
    {:env env :op :deftype* :as form :t t :fields fields :pmasks pmasks}))

(defmethod parse 'defrecord*
  [_ env [_ tsym fields pmasks :as form] _]
  (let [t (munge (:name (resolve-var (dissoc env :locals) tsym)))]
    (swap! namespaces update-in [(-> env :ns :name) :defs tsym]
           (fn [m]
             (let [m (assoc (or m {}) :name t)]
               (if-let [line (:line env)]
                 (-> m
                     (assoc :file *cljs-file*)
                     (assoc :line line))
                 m))))
    {:env env :op :defrecord* :form form :t t :fields fields :pmasks pmasks}))

;; dot accessor code

(def ^:private property-symbol? #(boolean (and (symbol? %) (re-matches #"^-.*" (name %)))))

(defn- munge-not-reserved [meth]
  (if-not (js-reserved (str meth))
    (munge meth)
    meth))

(defn- clean-symbol
  [sym]
  (symbol
   (if (property-symbol? sym)
     (-> sym name (.substring 1) munge-not-reserved)
     (-> sym name munge-not-reserved))))

(defn- classify-dot-form
  [[target member args]]
  [(cond (nil? target) ::error
         :default      ::expr)
   (cond (property-symbol? member) ::property
         (symbol? member)          ::symbol
         (seq? member)             ::list
         :default                  ::error)
   (cond (nil? args) ()
         :default    ::expr)])

(defmulti build-dot-form #(classify-dot-form %))

;; (. o -p)
;; (. (...) -p)
(defmethod build-dot-form [::expr ::property ()]
  [[target prop _]]
  {:dot-action ::access :target target :field (clean-symbol prop)})

;; (. o -p <args>)
(defmethod build-dot-form [::expr ::property ::list]
  [[target prop args]]
  (throw (Error. (str "Cannot provide arguments " args " on property access " prop))))

(defn- build-method-call
  "Builds the intermediate method call map used to reason about the parsed form during
  compilation."
  [target meth args]
  (if (symbol? meth)
    {:dot-action ::call :target target :method (munge-not-reserved meth) :args args}
    {:dot-action ::call :target target :method (munge-not-reserved (first meth)) :args args}))

;; (. o m 1 2)
(defmethod build-dot-form [::expr ::symbol ::expr]
  [[target meth args]]
  (build-method-call target meth args))

;; (. o m)
(defmethod build-dot-form [::expr ::symbol ()]
  [[target meth args]]
  (build-method-call target meth args))

;; (. o (m))
;; (. o (m 1 2))
(defmethod build-dot-form [::expr ::list ()]
  [[target meth-expr _]]
  (build-method-call target (first meth-expr) (rest meth-expr)))

(defmethod build-dot-form :default
  [dot-form]
  (throw (Error. (str "Unknown dot form of " (list* '. dot-form) " with classification " (classify-dot-form dot-form)))))

(defmethod parse '.
  [_ env [_ target & [field & member+] :as form] _]
  (disallowing-recur
   (let [{:keys [dot-action target method field args]} (build-dot-form [target field member+])
         enve        (assoc env :context :expr)
         targetexpr  (analyze enve target)]
     (case dot-action
           ::access {:env env :op :dot :form form
                     :target targetexpr
                     :field field
                     :children [targetexpr]
                     :tag (-> form meta :tag)}
           ::call   (let [argexprs (map #(analyze enve %) args)]
                      {:env env :op :dot :form form
                       :target targetexpr
                       :method method
                       :args argexprs
                       :children (into [targetexpr] argexprs)
                       :tag (-> form meta :tag)})))))

(defmethod parse 'js*
  [op env [_ jsform & args :as form] _]
  (assert (string? jsform))
  (if args
    (disallowing-recur
     (let [seg (fn seg [^String s]
                 (let [idx (.indexOf s "~{")]
                   (if (= -1 idx)
                     (list s)
                     (let [end (.indexOf s "}" idx)]
                       (cons (subs s 0 idx) (seg (subs s (inc end))))))))
           enve (assoc env :context :expr)
           argexprs (vec (map #(analyze enve %) args))]
       {:env env :op :js :segs (seg jsform) :args argexprs
        :tag (-> form meta :tag) :form form :children argexprs}))
    (let [interp (fn interp [^String s]
                   (let [idx (.indexOf s "~{")]
                     (if (= -1 idx)
                       (list s)
                       (let [end (.indexOf s "}" idx)
                             inner (:name (resolve-existing-var env (symbol (subs s (+ 2 idx) end))))]
                         (cons (subs s 0 idx) (cons inner (interp (subs s (inc end)))))))))]
      {:env env :op :js :form form :code (apply str (interp jsform))
       :tag (-> form meta :tag)})))

(defn parse-invoke
  [env [f & args :as form]]
  (disallowing-recur
   (let [enve (assoc env :context :expr)
         fexpr (analyze enve f)
         argexprs (vec (map #(analyze enve %) args))
         argc (count args)]
     (if (and *cljs-warn-fn-arity* (-> fexpr :info :fn-var))
       (let [{:keys [variadic max-fixed-arity method-params name]} (:info fexpr)]
         (when (and (not (some #{argc} (map count method-params)))
                    (or (not variadic)
                        (and variadic (< argc max-fixed-arity))))
           (warning env
             (str "WARNING: Wrong number of args (" argc ") passed to " name)))))
     {:env env :op :invoke :form form :f fexpr :args argexprs
      :tag (-> fexpr :info :tag) :children (into [fexpr] argexprs)})))

(defn analyze-symbol
  "Finds the var associated with sym"
  [env sym]
  (let [ret {:env env :form sym}
        lb (-> env :locals sym)]
    (if lb
      (assoc ret :op :var :info lb)
      (assoc ret :op :var :info (resolve-existing-var env sym)))))

(defn get-expander [sym env]
  (let [mvar
        (when-not (or (-> env :locals sym)        ;locals hide macros
                      (and (-> env :ns :excludes sym)
                           (not (-> env :ns :uses-macros sym))))
          (if-let [nstr (namespace sym)]
            (when-let [ns (cond
                           (= "clojure.core" nstr) (find-ns 'cljs.core)
                           (.contains nstr ".") (find-ns (symbol nstr))
                           :else
                           (-> env :ns :requires-macros (get (symbol nstr))))]
              (.findInternedVar ^clojure.lang.Namespace ns (symbol (name sym))))
            (if-let [nsym (-> env :ns :uses-macros sym)]
              (.findInternedVar ^clojure.lang.Namespace (find-ns nsym) sym)
              (.findInternedVar ^clojure.lang.Namespace (find-ns 'cljs.core) sym))))]
    (when (and mvar (.isMacro ^clojure.lang.Var mvar))
      @mvar)))

(defn macroexpand-1 [env form]
  (let [op (first form)]
    (if (specials op)
      form
      (if-let [mac (and (symbol? op) (get-expander op env))]
        (binding [*ns* *cljs-ns*]
          (apply mac form env (rest form)))
        (if (symbol? op)
          (let [opname (str op)]
            (cond
             (= (first opname) \.) (let [[target & args] (next form)]
                                     (list* '. target (symbol (subs opname 1)) args))
             (= (last opname) \.) (list* 'new (symbol (subs opname 0 (dec (count opname)))) (next form))
             :else form))
          form)))))

(defn analyze-seq
  [env form name]
  (let [env (assoc env :line
                   (or (-> form meta :line)
                       (:line env)))]
    (let [op (first form)]
      (assert (not (nil? op)) "Can't call nil")
      (let [mform (macroexpand-1 env form)]
        (if (identical? form mform)
          (if (specials op)
            (parse op env form name)
            (parse-invoke env form))
          (analyze env mform name))))))

(declare analyze-wrap-meta)

(defn analyze-map
  [env form name]
  (let [expr-env (assoc env :context :expr)
        simple-keys? (every? #(or (string? %) (keyword? %))
                             (keys form))
        ks (disallowing-recur (vec (map #(analyze expr-env % name) (keys form))))
        vs (disallowing-recur (vec (map #(analyze expr-env % name) (vals form))))]
    (analyze-wrap-meta {:op :map :env env :form form
                        :keys ks :vals vs :simple-keys? simple-keys?
                        :children (vec (interleave ks vs))}
                       name)))

(defn analyze-vector
  [env form name]
  (let [expr-env (assoc env :context :expr)
        items (disallowing-recur (vec (map #(analyze expr-env % name) form)))]
    (analyze-wrap-meta {:op :vector :env env :form form :items items :children items} name)))

(defn analyze-set
  [env form name]
  (let [expr-env (assoc env :context :expr)
        items (disallowing-recur (vec (map #(analyze expr-env % name) form)))]
    (analyze-wrap-meta {:op :set :env env :form form :items items :children items} name)))

(defn analyze-wrap-meta [expr name]
  (let [form (:form expr)]
    (if (meta form)
      (let [env (:env expr) ; take on expr's context ourselves
            expr (assoc-in expr [:env :context] :expr) ; change expr to :expr
            meta-expr (analyze-map (:env expr) (meta form) name)]
        {:op :meta :env env :form form
         :meta meta-expr :expr expr :children [meta-expr expr]})
      expr)))

(ann analyze [Env Any -> Expr])
(defn analyze
  "Given an environment, a map containing {:locals (mapping of names to bindings), :context
  (one of :statement, :expr, :return), :ns (a symbol naming the
  compilation ns)}, and form, returns an expression object (a map
  containing at least :form, :op and :env keys). If expr has any (immediately)
  nested exprs, must have :children [exprs...] entry. This will
  facilitate code walking without knowing the details of the op set."
  ([env form] (analyze env form nil))
  ([env form name]
     (let [form (if (instance? clojure.lang.LazySeq form)
                  (or (seq form) ())
                  form)]
       (cond
        (symbol? form) (analyze-symbol env form)
        (and (seq? form) (seq form)) (analyze-seq env form name)
        (map? form) (analyze-map env form name)
        (vector? form) (analyze-vector env form name)
        (set? form) (analyze-set env form name)
        :else {:op :constant :env env :form form}))))

(ann analyze-file [String -> (Seqable Expr)])
(defn analyze-file
  [f]
  (let [res (if (= \/ (first f)) f (io/resource f))]
    (assert res (str "Can't find " f " in classpath"))
    (binding [*cljs-ns* 'cljs.user
              *cljs-file* (.getPath ^java.net.URL res)]
      (with-open [r (io/reader res)]
        (let [env {:ns (@namespaces *cljs-ns*) :context :statement :locals {}}
              pbr (clojure.lang.LineNumberingPushbackReader. r)
              eof (Object.)]
          (loop [r (read pbr false eof false)]
            (let [env (assoc env :ns (@namespaces *cljs-ns*))]
              (when-not (identical? eof r)
                (analyze env r)
                (recur (read pbr false eof false))))))))))

(ann forms-seq (Fn [String -> (Seqable Form)]
                   [String PushbackReader -> (Seqable Form)]))
(defn forms-seq
  "Seq of forms in a Clojure or ClojureScript file."
  ([f]
     (forms-seq f (clojure.lang.LineNumberingPushbackReader. (io/reader f))))
  ([f ^java.io.PushbackReader rdr]
     (if-let [form (read rdr nil nil)]
       (lazy-seq (cons form (forms-seq f rdr)))
       (.close rdr))))

(ann rename-to-js [String -> String])
(defn rename-to-js
  "Change the file extension from .cljs to .js. Takes a File or a
  String. Always returns a String."
  [file-str]
  (clojure.string/replace file-str #".cljs$" ".js"))

(ann mkdirs [File -> Any])
(defn mkdirs
  "Create all parent directories for the passed file."
  [^java.io.File f]
  (.mkdirs (.getParentFile (.getCanonicalFile f))))

(defmacro with-core-cljs
  "Ensure that core.cljs has been loaded."
  [& body]
  `(do (when-not (:defs (get @namespaces 'cljs.core))
         (analyze-file "cljs/core.cljs"))
       ~@body))

(ann compile-file* [File File -> NsAnalysis])
(defn compile-file* [src dest]
  (with-core-cljs
    (with-open [out ^java.io.Writer (io/make-writer dest {})]
      (binding [*out* out
                *cljs-ns* 'cljs.user
                *cljs-file* (.getPath ^java.io.File src)
                *position* (atom [0 0])]
        (loop [forms (forms-seq src)
               ns-name nil
               deps nil]
          (if (seq forms)
            (let [env {:ns (@namespaces *cljs-ns*) :context :statement :locals {}}
                  ast (analyze env (first forms))]
              (do (emit ast)
                  (if (= (:op ast) :ns)
                    (recur (rest forms) (:name ast) (merge (:uses ast) (:requires ast)))
                    (recur (rest forms) ns-name deps))))
            {:ns (or ns-name 'cljs.user)
             :provides [ns-name]
             :requires (if (= ns-name 'cljs.core) (set (vals deps)) (conj (set (vals deps)) 'cljs.core))
             :file dest}))))))

(ann requires-compilation? [File File -> boolean])
(defn requires-compilation?
  "Return true if the src file requires compilation."
  [^java.io.File src ^java.io.File dest]
  (or (not (.exists dest))
      (> (.lastModified src) (.lastModified dest))))

(ann compile-file (Fn [String -> NsAnalysis]
                      [String String -> NsAnalysis]))
(defn compile-file
  "Compiles src to a file of the same name, but with a .js extension,
   in the src file's directory.

   With dest argument, write file to provided location. If the dest
   argument is a file outside the source tree, missing parent
   directories will be created. The src file will only be compiled if
   the dest file has an older modification time.

   Both src and dest may be either a String or a File.

   Returns a map containing {:ns .. :provides .. :requires .. :file ..}.
   If the file was not compiled returns only {:file ...}"
  ([src]
     (let [dest (rename-to-js src)]
       (compile-file src dest)))
  ([src dest]
     (let [src-file (io/file src)
           dest-file (io/file dest)]
       (if (.exists src-file)
         (if (requires-compilation? src-file dest-file)
           (do (mkdirs dest-file)
               (compile-file* src-file dest-file))
           {:file dest-file})
         (throw (java.io.FileNotFoundException. (str "The file " src " does not exist.")))))))

(comment
  ;; flex compile-file
  (do
    (compile-file "/tmp/hello.cljs" "/tmp/something.js")
    (slurp "/tmp/hello.js")

    (compile-file "/tmp/somescript.cljs")
    (slurp "/tmp/somescript.js")))

(defn path-seq
  [file-str]
  (->> java.io.File/separator
       java.util.regex.Pattern/quote
       re-pattern
       (string/split file-str)))

(defn to-path
  ([parts]
     (to-path parts java.io.File/separator))
  ([parts sep]
     (apply str (interpose sep parts))))

(defn to-target-file
  "Given the source root directory, the output target directory and
  file under the source root, produce the target file."
  [^java.io.File dir ^String target ^java.io.File file]
  (let [dir-path (path-seq (.getAbsolutePath dir))
        file-path (path-seq (.getAbsolutePath file))
        relative-path (drop (count dir-path) file-path)
        parents (butlast relative-path)
        parent-file (java.io.File. ^String (to-path (cons target parents)))]
    (java.io.File. parent-file ^String (rename-to-js (last relative-path)))))

(defn cljs-files-in
  "Return a sequence of all .cljs files in the given directory."
  [dir]
  (filter #(let [name (.getName ^java.io.File %)]
             (and (.endsWith name ".cljs")
                  (not= \. (first name))
                  (not (contains? cljs-reserved-file-names name))))
          (file-seq dir)))

(defn compile-root
  "Looks recursively in src-dir for .cljs files and compiles them to
   .js files. If target-dir is provided, output will go into this
   directory mirroring the source directory structure. Returns a list
   of maps containing information about each file which was compiled
   in dependency order."
  ([src-dir]
     (compile-root src-dir "out"))
  ([src-dir target-dir]
     (let [src-dir-file (io/file src-dir)]
       (loop [cljs-files (cljs-files-in src-dir-file)
              output-files []]
         (if (seq cljs-files)
           (let [cljs-file (first cljs-files)
                 output-file ^java.io.File (to-target-file src-dir-file target-dir cljs-file)
                 ns-info (compile-file cljs-file output-file)]
             (recur (rest cljs-files) (conj output-files (assoc ns-info :file-name (.getPath output-file)))))
           output-files)))))