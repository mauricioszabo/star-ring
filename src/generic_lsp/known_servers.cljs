(ns generic-lsp.known-servers)

(def js-ts-server {:args ["--stdio"]
                   :binary "typescript-language-server"})
(def servers
  {"Clojure" {:args []
              :binary "clojure-lsp"}
   "C++" {:args []
          :binary "clangd"}
   "C" {:args []
        :binary "clangd"}
   "JavaScript" js-ts-server
   "Babel ES6 JavaScript" js-ts-server
   "TypeScript" js-ts-server
   "TypeScriptReact" js-ts-server
   "Ruby" {:args ["exec" "solargraph" "stdio"]
           :binary "bundle"}
   "Java" {:binary "jdtls"
           :args []}
   "Rust" {:binary "rust-analyzer"
           :args []}
   "Lua" {:binary "lua-language-server"
          :args []}})
