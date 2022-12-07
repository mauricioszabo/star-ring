const ring = require('../star-ring')

module.exports.activate = ring.generic_lsp_activate
module.exports.deactivate = ring.generic_lsp_deactivate
module.exports.config = ring.generic_lsp_config
module.exports.provider = ring.generic_lsp_provider
module.exports.status_bar_consumer = ring.generic_lsp_status_bar_consumer
module.exports.complete_provider = ring.generic_lsp_complete_provider
module.exports.intentions_list = ring.generic_lsp_intentions_list
module.exports.intentions_provide = ring.generic_lsp_intentions_provide
module.exports.datatip_consumer = ring.generic_lsp_datatip_consumer
module.exports.linter_consumer = ring.generic_lsp_linter_consumer
