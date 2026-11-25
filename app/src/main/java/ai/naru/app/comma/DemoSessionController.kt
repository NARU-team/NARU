package ai.naru.app.comma

/** Only the demo composition root uses this clock. Production clock is never adjusted. */
private class DemoClock(private val real: RestClock) : RestClock {
    private var offset=0L
    override fun elapsedMillis()=real.elapsedMillis()+offset
    override fun wallMillis()=real.wallMillis()+offset
    fun advance(millis:Long) {require(millis>=0);offset+=millis}
}

/** Isolated real manager + virtual clock/cabin/vehicle. No history or real vehicle repository. */
class DemoSessionController(realClock:RestClock) {
    private val clock=DemoClock(realClock)
    val cabin:CabinController=AppCabinController()
    private val manager=RestSessionManager(clock,cabin) {"demo-"+java.util.UUID.randomUUID()}
    val state=manager.state
    val active get()=manager.active
    var vehicle=DemoVehicleState()
        private set
    private var previousVehicle=vehicle
    private var configuredSettings=CabinSettings()
    fun wallMillis()=clock.wallMillis()

    fun prepare(minutes:Int,settings:CabinSettings) {
        check(!active)
        if(state.value.phase==RestPhase.FINISHED) manager.prepareNext()
        selectMinutes(minutes)
        configure(settings)
    }
    fun selectMinutes(minutes:Int) {manager.selectMinutes(minutes)}
    fun configure(settings:CabinSettings) {
        if(active) return
        configuredSettings=settings
        manager.configure(settings)
    }
    /** Backward-compatible test/helper entry; filming enters READY via prepare(). */
    fun start(minutes:Int,settings:CabinSettings) {
        check(!active)
        prepare(minutes,settings)
        check(startPrepared())
    }
    fun startPrepared(mode:DemoRestMode=DemoRestMode.CONFIGURED):Boolean {
        if(state.value.phase!=RestPhase.READY || vehicle.vehicle.parkingStatus!=ParkingStatus.PARKED) return false
        previousVehicle=vehicle
        manager.configure(if(mode==DemoRestMode.ALARM_ONLY) cabin.settings.value else configuredSettings)
        if(!manager.start()) return false
        vehicle=vehicle.copy(mode=mode,restored=false,
            climateApplied=mode==DemoRestMode.CONFIGURED && vehicle.climateRequested &&
                vehicle.climateSupported && !vehicle.climateFailed,
            lightingApplied=if(mode==DemoRestMode.CONFIGURED) state.value.settings.lighting else vehicle.lightingApplied)
        return true
    }
    private fun syncRestoration() {
        if(state.value.phase==RestPhase.FINISHED && !vehicle.restored) {
            // Parking/temperature/battery signals are never rewritten by settings restoration.
            vehicle=vehicle.copy(climateApplied=previousVehicle.climateApplied,
                lightingApplied=previousVehicle.lightingApplied,restored=true)
        }
    }
    fun tick() {manager.tick();syncRestoration()}
    fun extend(minutes:Int)=manager.extend(minutes)
    fun finish() {manager.finish();syncRestoration()}
    fun prepareNext():Boolean {
        if(!manager.prepareNext()) return false
        manager.configure(configuredSettings)
        vehicle=vehicle.copy(restored=false,mode=DemoRestMode.CONFIGURED)
        return true
    }
    fun setParking(status:ParkingStatus) {
        vehicle=vehicle.copy(vehicle=vehicle.vehicle.copy(parked=when(status) {
            ParkingStatus.PARKED -> true
            ParkingStatus.NOT_PARKED -> false
            ParkingStatus.UNKNOWN -> null
        },driving=DrivingStatus.UNKNOWN))
    }
    fun setEnvironment(warm:Boolean) {
        vehicle=vehicle.copy(vehicle=vehicle.vehicle.copy(
            indoorCelsius=if(warm)28f else 23f,outdoorCelsius=if(warm)32f else 22f,
            batteryPercent=if(warm)75 else 80),climateTarget=if(warm)24f else 23f)
    }
    fun setClimateSupported(supported:Boolean) {
        vehicle=vehicle.copy(climateSupported=supported,
            climateApplied=if(supported)vehicle.climateApplied else false)
    }
    fun failClimate() {vehicle=vehicle.copy(climateFailed=true,climateApplied=false)}
    fun requestClimate():Boolean {
        if(!vehicle.climateSupported) return false
        vehicle=vehicle.copy(climateFailed=false,climateRequested=true,climateConfigured=true,
            climateApplied=active && vehicle.mode==DemoRestMode.CONFIGURED)
        return true
    }
    fun simulateDrivingStart():Boolean {
        if(!active) return false
        vehicle=vehicle.copy(vehicle=vehicle.vehicle.copy(parked=false,driving=DrivingStatus.DRIVING))
        manager.finish(EndReason.DRIVING)
        syncRestoration()
        return true
    }
    fun advanceToLastThirtySeconds() {
        tick();if(state.value.phase!=RestPhase.RESTING)return
        clock.advance((state.value.remainingMillis-30_000L).coerceAtLeast(0));tick()
    }
    fun advanceToDeadline() {
        tick();if(state.value.phase!=RestPhase.RESTING)return
        clock.advance(state.value.remainingMillis);tick()
    }
    fun expireConfirmation() {
        tick();if(state.value.phase!=RestPhase.WAKING)return
        clock.advance(state.value.confirmationRemainingMillis);tick()
    }
}
