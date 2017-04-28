using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Input;
using Xamarin.Forms;

namespace MobileSyncModels.Services
{
    public interface ISynchronizationCommand
    {
        Command Command { get; }
        Func<SynchronizationParameters, SynchronizationParameters> ParameterEnhancer { get; set; }
    }
}
