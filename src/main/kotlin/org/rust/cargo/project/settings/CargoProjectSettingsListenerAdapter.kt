package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.settings.DelegatingExternalSystemSettingsListener
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener

class CargoProjectSettingsListenerAdapter(delegate: ExternalSystemSettingsListener<CargoProjectSettings>)
    : DelegatingExternalSystemSettingsListener<CargoProjectSettings>(delegate), CargoProjectSettingsListener