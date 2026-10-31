package edu.jxslu.schedule.ui.tile

/** 一卡通付款码磁贴（DESIGN §3.32）：点击直达付款码页；未配置凭证时进校园卡设置。 */
class PayCodeTileService : JuwTileService() {
    override val kind: TileKind = TileKind.PAY_CODE
}
