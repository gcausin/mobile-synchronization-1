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
        Command<string> Command { get; }
        Func<SynchronizationParameters, string, SynchronizationParameters> ParameterEnhancer { get; set; }
    }
}
