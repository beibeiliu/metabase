(ns metabase.driver.vertica-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [metabase.driver.sql-jdbc
             [connection :as sql-jdbc.conn]
             [sync :as sql-jdbc.sync]]
            [metabase.test :as mt]
            [metabase.test.util :as tu]))

(deftest db-timezone-test
  (mt/test-driver :vertica
    (is (= "UTC" (tu/db-timezone-id)))))

(deftest additional-connection-string-options-test
  (mt/test-driver :vertica
    (testing "Make sure you can add additional connection string options (#6651)"
      (is (= {:classname   "com.vertica.jdbc.Driver"
              :subprotocol "vertica"
              :subname     "//localhost:5433/birds-near-me?ConnectionLoadBalance=1"}
             (sql-jdbc.conn/connection-details->spec :vertica {:host               "localhost"
                                                               :port               5433
                                                               :db                 "birds-near-me"
                                                               :additional-options "ConnectionLoadBalance=1"}))))))

(deftest determine-select-privilege
  (mt/test-driver :vertica
    (testing "Do we correctly determine SELECT privilege"
      (let [details (:details (mt/db))
            spec    (sql-jdbc.conn/connection-details->spec :vertica details)]
        (doseq [statement ["drop table if exists birds;"
                           "create table birds (id integer);"
                           (format "grant all on birds to %s;" (:user details))]]
          (jdbc/execute! spec [statement]))
        (is (#'sql-jdbc.sync/have-select-privilege? :vertica db {:table_name  "birds"
                                                                 :table_schem "public"}))
        (jdbc/execute! spec [(format "revoke select on birds from %s;" (:user details))])
        (is (not (#'sql-jdbc.sync/have-select-privilege? :vertica db {:table_name  "birds"
                                                                      :table_schem "public"})))
        ;; Cleanup
        (jdbc/execute! spec ["drop table birds;"])))))
