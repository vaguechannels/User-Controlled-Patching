# User Controlled Patching
## **Business Case**
* Users can’t control the installation start-time at their endpoint.
* Allow User to control software installation at their machine by installing it at that moment or post-pone it till the next update schedule.
* Avoid software downtime during working hours.
***
## **User Controlled Deployment**
* Feature introduced in 8.1.01
* Pre-requisite Properties

     * marimba.subscription.usercontrolled.enabled = true
     * marimba.tuner.display.nodisplay = false
     * marimba.subscription.usercontrolled.stage = true
     * marimba.tuner.trayicon**.enabled=false

** - Multiple properties involved.  Please refer to BMC BladeLogic Client Automation Reference Guide for more details.

* On policy update, a balloon will be displayed

 ![BubbleViewSoftwareUpdates](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/BubbleViewSoftwareUpdates.png).

* On right click on the UCS Icon and selecting View Software Details option, BMC Software Installation window will be displayed. 

![BubbleNewSoftware](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/BubbleNewSoftware.png).

* Software Installation Window
  * User can Click Install Now to install the packages immediately.
  * Choose Snooze to postpone the installation for the chosen time.

![SoftwareInstallationWindow](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/SoftwareInstallationWindow.png).
***
## **Patching – User Controlled?**
* Patch Reboots are User Controlled via CRS.
* How can Patch Deployment be User Controlled?
* Build Custom Package using Marimba API.
* Setup Policy for Targets with Custom Package.
* Reporting – Who initiated Patch Deployment? (Reporting Value-add)

***

## **Architecture – Workflow (Initial Design)**
![UserControlledPatching_InitialWorkflow](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_InitialWorkflow.png).

***

## **Architecture – Workflow (Updated Design)**
![UserControlledPatching_UpdatedWorkflow](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_UpdatedWorkflow.png).

***

## **Configuration Setup**
 * Channel Parameters
   * patchgroupurl=http://Transmitter:5282/PatchManagement/PatchGroups/Client_Skype_PatchGroup
   * patchserviceurl=http://Transmitter:5282/Marimba/Current/PatchService
   * preinstall.arguments=-deployPatchGroup Client_Skype_PatchGroup doNotDeleteGroups
   * preuninstall.arguments=-remove
   * timeout=90
 * Debug Flag – Tuner Property
   * marimba.usercontrolledpatch.debug.enabled=true/false

![UserControlledPatching_PackageEditorAddScript](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_PackageEditorAddScript.png).
***
![UserControlledPatching_PackageEditorChannelProperties](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_PackageEditorChannelProperties.png).
***
![UserControlledPatching_SetupPolicy](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_SetupPolicy.png).
***
## **Demo**
![BubbleViewSoftwareUpdates](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/BubbleViewSoftwareUpdates.png).

![BubbleNewSoftware](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/BubbleNewSoftware.png).

![UserControlledPatching_DemoSoftwareInstallationWindow](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_DemoSoftwareInstallationWindow.png).
***
## Reporting
 * Snapshot of Registry Entries

![UserControlledPatching_RegistryEntries](https://github.com/vaguechannels/User-Controlled-Patching/blob/master/UserControlledPatch/Files/UserControlledPatching_RegistryEntries.png).
