const ring = require('../star-ring')

module.exports.activate = ring.generic-lsp-activate
module.exports.deactivate = ring.generic-lsp-deactivate
module.exports.config = ring.generic-lsp-config
module.exports.provider = ring.generic-lsp-provider
module.exports.status-bar-consumer = ring.generic-lsp-status-bar-consumer
module.exports.complete-provider = ring.generic-lsp-complete-provider
module.exports.intentions-list = ring.generic-lsp-intentions-list
module.exports.intentions-provide = ring.generic-lsp-intentions-provide
module.exports.datatip-consumer = ring.generic-lsp-datatip-consumer
module.exports.linter-consumer = ring.generic-lsp-linter-consumer
