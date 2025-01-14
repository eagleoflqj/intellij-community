package com.intellij.ide.starter.build.tool

import com.intellij.ide.starter.ide.IDETestContext

/**
 * Handles different stuff, that is related to that particular build tool
 */
open class BuildTool(val type: BuildToolType, val testContext: IDETestContext)

