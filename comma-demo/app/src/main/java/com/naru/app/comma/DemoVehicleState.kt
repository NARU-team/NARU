package com.naru.app.comma

enum class DemoRestMode { CONFIGURED, ALARM_ONLY }

/** Scripted values for filming, never real measurements or vehicle acknowledgements. */
data class DemoVehicleState(
    val vehicle:VehicleState=VehicleState(28f,32f,true,75),
    val climateSupported:Boolean=true,
    val climateRequested:Boolean=true,
    val climateConfigured:Boolean=false,
    val climateFailed:Boolean=false,
    val climateApplied:Boolean=false,
    val climateTarget:Float=24f,
    val lightingApplied:Float=0f,
    val restored:Boolean=false,
    val mode:DemoRestMode=DemoRestMode.CONFIGURED
) {
    fun controlDisplay()=VehicleControlState(
        if(!climateSupported) ControlOutcome.UNSUPPORTED else if(climateFailed) ControlOutcome.FAILURE
        else ControlOutcome.NOT_REQUESTED,
        when {
            !climateSupported -> "DEMO 가상 차량 제어 미지원 · 실제 API 호출 없음"
            climateFailed -> "DEMO 가상 실패 응답 · 실제 API 호출 없음"
            restored -> "DEMO 가상 설정 복구 완료 · 실제 차량 제어 없음"
            mode==DemoRestMode.ALARM_ONLY -> "DEMO 알람 전용 · 가상 설정 적용 없음"
            climateConfigured && !climateApplied -> "DEMO 가상 ${climateTarget}°C 설정 완료 · 시작 시 적용"
            climateApplied -> "DEMO 가상 공조 ${climateTarget}°C 적용 · 실제 차량 제어 없음"
            else -> "DEMO 가상 설정 대기 · 실제 차량 제어 없음"
        })
}
