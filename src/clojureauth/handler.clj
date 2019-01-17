(ns clojureauth.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.response :refer [Renderable]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [buddy.auth.backends]
            [buddy.hashers :as hashers]
            [buddy.auth.accessrules :refer [restrict]]
            [hiccup.core :as h]
            [hiccup.element :as he]
            [hiccup.page :as hp]
            [hiccup.form :as hf]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))


;;; "DATABASE"
(def users (atom {}))

(defn get-user [username]
  (get @users username))

(defn add-user! [username password]
  (if (get-user (str/lower-case username))
    (throw (Exception. "User already exists!"))
    (swap! users assoc (str/lower-case username) {:username username
                                                  :password password})))

;;; PASSWORD
(defn password-hash
  [password]
  (hashers/derive password {:alg :bcrypt+sha512}))

(defn validate-password [username password]
  (hashers/check password (get (get-user username) :password)))


;;; USER FUNCTIONALITY

(defn add-user-to-session [request user]
  (assoc-in request [:session :identity] user)) ; http://www.luminusweb.net/docs/routes.html#restricting_access

(defn clear-session-identity [request]
  (assoc-in request [:session :identity] nil))


(defn register-user [{params :params}]
  (let [username (get params "user/name")
        password (get params "user/password")
        hash     (password-hash password)]
    (add-user! username hash))
  [:h1 "Great! Now " (he/link-to "/login" "login") " or " (he/link-to "/" "go back!")])


(defn login-user [{params :params :as request}]
  (let [user     (-> (get params "user/name")
                     (str/lower-case))
        password (get params "user/password")]
    (if (validate-password user password)
      (-> (hp/html5 [:div
                     [:h1 "You are now logged in!"]
                     [:div (he/link-to "/request" "Confirm the new identity in your session data")]
                     [:div (he/link-to "/protected" "Go to the protected page")]])
          (response/response)
          (response/content-type "text/html; charset=utf-8")
          (add-user-to-session user)) ;; check out your fresh new identity in /request
      [:div
       [:h1 "Login not successful!"]
       (he/link-to "/" "Back")])))

(defn logout-user [req]
  (-> (hp/html5 [:div
                 [:h1 "You are now logged out!"]
                 [:div (he/link-to "/" "Back")]
                 [:div (he/link-to "/protected" "Go to the protected page")]])
      (response/response)
      (response/content-type "text/html; charset=utf-8")
      (clear-session-identity)))

;;; PAGES

(defn page-start []
  [:ul
   [:li (he/link-to "/register" "Register" )]
   [:li (he/link-to "/login" "Login" )]
   [:li (he/link-to "/protected" "Protected Page (try accessing it!)")]
   [:li (he/link-to "/request" "Check out your request & session")]
   [:li (he/link-to "/logout" "Logout")]
   ])

(defn page-register []
  (hf/form-to [:post "/register"]
              [:div
               (hf/label "user/name" "Username")
               (hf/text-field "user/name")]
              [:div
               (hf/label "user/password" "Password")
               (hf/text-field "user/password")]
              [:div
               (hf/submit-button "Register")]))

(defn page-login []
  (hf/form-to [:post "/login"] ;; change this to [:post "/request"] to see what's being sent
              [:div
               (hf/label "user/name" "Username")
               (hf/text-field "user/name")]
              [:div
               (hf/label "user/password" "Password")
               (hf/text-field "user/password")]
              [:div
               (hf/submit-button "Login")]))

(defn page-protected [{{identity :identity} :session}]
  (let [username (-> (get-user identity) :username)]
    [:div
     [:h1 "Welcome to your protected page, " username "!"]
     [:div (he/link-to "/" "Back")]]))

(defn show-request [req]
  [:div
   [:pre (with-out-str (pprint/pprint req))]
   [:div (he/link-to "/" "Back")]])

;;; MIDDLEWARES

(defn on-error [request response]
  {:status  403
   :headers {"Content-Type" "text/html"}
   :body    (str "Access to " (:uri request) " is not authorized. <br><a href='/login'> Login </a><br><a href='/'> Back </a")})

(defn authenticated? [request]
  (boolean (get-in request [:session :identity])))

(defn wrap-restricted [handler]
  (restrict handler {:handler  authenticated?
                     :on-error on-error}))

;; Automatically wrap hiccup
(extend-protocol Renderable
  clojure.lang.PersistentVector
  (render [body _]
    (-> (hp/html5 body)
        (response/response)
        (response/content-type "text/html; charset=utf-8"))))


;;; ROUTES

(defroutes app-routes
  (GET "/" [] (page-start))
  (GET "/register" [] (page-register))
  (POST "/register" req (register-user req))
  (GET "/login" [] (page-login))
  (POST "/login" req (login-user req))
  (GET "/logout" req (logout-user req))
  (GET "/request" req (show-request req))
  (POST "/request" req (show-request req))
  (route/not-found "Not Found"))

(defroutes protected-routes
  (GET "/protected" req (page-protected req)))

(defn wrap-base [handler]
  (-> handler
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)))
      (wrap-authentication (buddy.auth.backends/session))
      (wrap-stacktrace)))

(def app
  (wrap-base
   (routes
    (wrap-routes #'protected-routes wrap-restricted)
    #'app-routes)))


;; Run the webserver ; either like this in the repl or using "lein ring server"
(defn -main []
  (jetty/run-jetty (wrap-reload #'app) {:port 3000 :join? false}))
